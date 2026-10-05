package com.ecom.level3.messaging;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.ecom.level3.service.SyncService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Strategy A — dual-write consumer (RabbitMQ -&gt; ES).
 * Re-reads the LATEST order state from PostgreSQL before indexing.
 * Idempotent: document id = PostgreSQL order id.
 */
@Component
public class OrderSyncListener {

  private static final Logger log = LoggerFactory.getLogger(OrderSyncListener.class);

  private final SyncService sync;

  public OrderSyncListener(SyncService sync) {
    this.sync = sync;
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @RabbitListener(queues = "${app.sync.queue:level3_order_sync}",
      containerFactory = "syncListenerFactory")
  public void onOrderSync(Object message) {
    Long orderId = toOrderId(unwrap(message));
    if (orderId == null) {
      log.warn("[dual-write] Ignoring unparseable sync message: {}", message);
      return;
    }
    Map<String, Object> result = sync.upsertOrderDocument(orderId);
    if (Boolean.TRUE.equals(result.get("synced"))) {
      log.info("[dual-write] Synchronized order {} to Elasticsearch", orderId);
    } else {
      log.warn("[dual-write] Order {} not synced: {}", orderId, result.get("reason"));
    }
  }

  /**
   * Unwrap a raw Spring AMQP message (defensive: works whether or not a
   * container message converter was applied) into a Long / Map / String.
   */
  public static Object unwrap(Object message) {
    if (message instanceof Message m) {
      String text = new String(m.getBody(), StandardCharsets.UTF_8).strip();
      if (text.isEmpty()) {
        return null;
      }
      try {
        return Long.parseLong(text);
      } catch (NumberFormatException ignored) {
      }
      try {
        return MAPPER.readValue(text, Map.class);
      } catch (Exception ignored) {
      }
      return text;
    }
    return message;
  }

  public static Long toOrderId(Object message) {
    if (message instanceof Number n) {
      return n.longValue();
    }
    if (message instanceof String s) {
      try {
        return Long.parseLong(s.strip());
      } catch (NumberFormatException ex) {
        return null;
      }
    }
    if (message instanceof Map<?, ?> m) {
      Object id = m.get("orderId");
      if (id == null) {
        id = m.get("order_id");
      }
      if (id instanceof Number n) {
        return n.longValue();
      }
      if (id != null) {
        try {
          return Long.parseLong(String.valueOf(id).strip());
        } catch (NumberFormatException ex) {
          return null;
        }
      }
    }
    return null;
  }
}
