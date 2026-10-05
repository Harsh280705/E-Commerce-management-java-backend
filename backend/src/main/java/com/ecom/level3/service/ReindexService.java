package com.ecom.level3.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ecom.level3.model.Level3Order;
import com.ecom.level3.repository.OrderRepository;
import com.ecom.level3.search.ElasticOrderIndex;

/** Rebuild the Level 3 Elasticsearch index from Level 3 PostgreSQL (source of truth). */
@Service
public class ReindexService {

  private static final Logger log = LoggerFactory.getLogger(ReindexService.class);

  private final OrderRepository orders;
  private final SyncService sync;
  private final ElasticOrderIndex es;

  public ReindexService(OrderRepository orders, SyncService sync, ElasticOrderIndex es) {
    this.orders = orders;
    this.sync = sync;
    this.es = es;
  }

  /** Delete + recreate the index, then bulk-index every PG order (idempotent). */
  public Map<String, Object> rebuildIndex() {
    es.deleteIndex();
    es.ensureIndex();
    List<Long> ids = orders.findAll().stream().map(Level3Order::getId).sorted().toList();
    List<Long> synced = sync.bulkUpsertOrders(ids);
    es.refresh();
    log.info("Rebuilt Elasticsearch index {} from PostgreSQL: {}/{} docs",
        es.getIndex(), synced.size(), ids.size());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("pg_orders", ids.size());
    out.put("es_docs", synced.size());
    out.put("index", es.getIndex());
    return out;
  }
}
