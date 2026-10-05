package com.ecom.level3.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.level3.model.Level3Order;
import com.ecom.level3.repository.OrderRepository;
import com.ecom.level3.search.ElasticOrderIndex;

/**
 * Order synchronization: PostgreSQL -&gt; Elasticsearch (TWO strategies).
 *
 * <p>STRATEGY A — DUAL WRITE (event-driven): the API commits to PostgreSQL,
 * publishes the order id to RabbitMQ; the listener re-reads the LATEST rows
 * from PostgreSQL, builds the ES document FROM POSTGRESQL ONLY and upserts it
 * with document id = order id (idempotent). ES failures never roll back PG.
 *
 * <p>STRATEGY B — PERIODIC POLLING (bulk safety net via scheduler): finds PG
 * orders created/updated in the lookback window and bulk-upserts them.
 */
@Service
public class SyncService {

  private static final Logger log = LoggerFactory.getLogger(SyncService.class);

  private final OrderRepository orders;
  private final ElasticOrderIndex es;
  private final int lookbackSeconds;

  public SyncService(OrderRepository orders, ElasticOrderIndex es,
      @Value("${app.poll.lookback-seconds:180}") int lookbackSeconds) {
    this.orders = orders;
    this.es = es;
    this.lookbackSeconds = lookbackSeconds;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> buildDocumentTx(long orderId) {
    return orders.findByIdWithItems(orderId).map(OrderService::toOrderDocument).orElse(null);
  }

  /** Shared idempotent upsert used by BOTH strategies + reindex. */
  public Map<String, Object> upsertOrderDocument(long orderId) {
    Map<String, Object> doc = buildDocumentTx(orderId);
    if (doc == null) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("order_id", orderId);
      out.put("synced", false);
      out.put("reason", "not_found");
      return out;
    }
    es.ensureIndex();
    es.indexDocument(orderId, doc);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("order_id", orderId);
    out.put("synced", true);
    return out;
  }

  /** Bulk-synchronize PG orders into ES (Strategy B + reindex). */
  public List<Long> bulkUpsertOrders(List<Long> orderIds) {
    Map<Long, Map<String, Object>> docs = new LinkedHashMap<>();
    for (Long id : orderIds) {
      Map<String, Object> doc = buildDocumentTx(id);
      if (doc != null) {
        docs.put(id, doc);
      }
    }
    if (docs.isEmpty()) {
      return List.of();
    }
    es.ensureIndex();
    es.bulkIndex(docs);
    return new ArrayList<>(docs.keySet());
  }

  /** Strategy B tick: bulk-sync orders changed inside the lookback window. */
  public Map<String, Object> pollChangedOrders(String strategyTag) {
    try {
      es.ensureIndex();
    } catch (Exception ex) {
      log.warn("[{}] ES unavailable, will retry next tick: {}", strategyTag, ex.getMessage());
      return Map.of("checked", 0, "synced", List.of(), "reason", "es_unavailable");
    }
    Instant cutoff = Instant.now().minus(lookbackSeconds, ChronoUnit.SECONDS);
    List<Level3Order> rows =
        orders.findByUpdatedAtGreaterThanEqualOrderByIdDesc(cutoff, PageRequest.of(0, 500));
    List<Long> ids = rows.stream().map(Level3Order::getId).toList();
    List<Long> synced;
    try {
      synced = bulkUpsertOrders(ids);
    } catch (Exception ex) {
      log.warn("[{}] Bulk sync failed: {}", strategyTag, ex.getMessage());
      return Map.of("checked", ids.size(), "synced", List.of(), "reason", "bulk_failed");
    }
    if (!synced.isEmpty()) {
      log.info("[{}] Bulk-synchronized {} orders: {}", strategyTag, synced.size(), synced);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("checked", ids.size());
    out.put("synced", synced);
    return out;
  }
}
