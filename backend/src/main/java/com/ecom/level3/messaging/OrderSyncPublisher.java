package com.ecom.level3.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publishes order-sync events to RabbitMQ. Call ONLY after a successful
 * PostgreSQL commit — a publish failure must NOT roll back the order
 * (the polling sync picks it up instead).
 */
@Component
public class OrderSyncPublisher {

  private static final Logger log = LoggerFactory.getLogger(OrderSyncPublisher.class);

  private final RabbitTemplate rabbit;
  private final String exchange;
  private final String routingKey;

  public OrderSyncPublisher(RabbitTemplate rabbit,
      @Value("${app.sync.exchange:level3_orders}") String exchange,
      @Value("${app.sync.queue:level3_order_sync}") String routingKey) {
    this.rabbit = rabbit;
    this.exchange = exchange;
    this.routingKey = routingKey;
  }

  public boolean publishOrderSync(long orderId) {
    try {
      rabbit.convertAndSend(exchange, routingKey, orderId);
      log.info("Published sync task for order {}", orderId);
      return true;
    } catch (Exception ex) {
      log.error("Failed to publish sync task for order {}", orderId, ex);
      return false;
    }
  }
}
