package com.ecom.level3.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ecom.level3.dto.Dtos.OrderCreate;
import com.ecom.level3.dto.Dtos.OrderOut;
import com.ecom.level3.dto.Dtos.OrderStatusUpdate;
import com.ecom.level3.dto.Dtos.OrderSyncStatus;
import com.ecom.level3.exception.UnprocessableException;
import com.ecom.level3.messaging.OrderSyncPublisher;
import com.ecom.level3.model.Level3Order;
import com.ecom.level3.search.ElasticOrderIndex;
import com.ecom.level3.service.OrderService;
import com.ecom.level3.service.OrderStatuses;

import jakarta.validation.Valid;

/**
 * Order endpoints. Write path: validate user (PG) + products (MongoDB
 * snapshot), commit orders + order_items atomically to PostgreSQL, THEN
 * publish ES sync. Read path: canonical data from PostgreSQL, never ES.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private static final Logger log = LoggerFactory.getLogger(OrderController.class);

  private final OrderService orders;
  private final OrderSyncPublisher publisher;
  private final ElasticOrderIndex es;

  public OrderController(OrderService orders, OrderSyncPublisher publisher, ElasticOrderIndex es) {
    this.orders = orders;
    this.publisher = publisher;
    this.es = es;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public OrderOut createOrder(@Valid @RequestBody OrderCreate payload) {
    Level3Order created = orders.createOrder(payload);
    // ONLY AFTER successful commit: synchronize to Elasticsearch.
    if (!publisher.publishOrderSync(created.getId())) {
      log.warn("Order {} committed but sync publish failed; poller will backfill", created.getId());
    }
    return OrderService.toOrderOut(orders.getOrderOrThrow(created.getId()));
  }

  /** Order listing from PostgreSQL (transactional data, e.g. a user's orders). */
  @GetMapping
  @Transactional(readOnly = true)
  public List<OrderOut> listOrders(
      @RequestParam(name = "user_id", required = false) Long userId,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "page", defaultValue = "1") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    if (page < 1) {
      throw new UnprocessableException("page must be >= 1");
    }
    if (size < 1 || size > 100) {
      throw new UnprocessableException("size must be between 1 and 100");
    }
    String norm = null;
    if (status != null && !status.isBlank()) {
      norm = OrderStatuses.requireValid(status);
    }
    String filter = norm;
    return orders.listOrders(userId, filter, page, size).stream()
        .map(OrderService::toOrderOut)
        .toList();
  }

  /** Canonical order details — always from PostgreSQL, never ES. */
  @GetMapping("/{orderId}")
  @Transactional(readOnly = true)
  public OrderOut getOrder(@PathVariable long orderId) {
    return OrderService.toOrderOut(orders.getOrderOrThrow(orderId));
  }

  @PatchMapping("/{orderId}/status")
  public OrderOut updateOrderStatus(@PathVariable long orderId,
      @RequestBody OrderStatusUpdate payload) {
    if (payload == null || payload.status() == null) {
      throw new UnprocessableException("status must be one of " + OrderStatuses.ALL);
    }
    Level3Order updated = orders.updateStatus(orderId, payload.status());
    if (!publisher.publishOrderSync(updated.getId())) {
      log.warn("Order {} status committed but sync publish failed; poller will backfill", updated.getId());
    }
    return OrderService.toOrderOut(orders.getOrderOrThrow(orderId));
  }

  /** Compare PostgreSQL updated_at with the Elasticsearch projection. */
  @GetMapping("/{orderId}/sync")
  @Transactional(readOnly = true)
  public OrderSyncStatus orderSyncStatus(@PathVariable long orderId) {
    Level3Order order = orders.getOrderOrThrow(orderId);
    String esUpdated = null;
    try {
      Map<String, Object> doc = es.getDocument(orderId);
      if (doc != null && doc.get("updated_at") != null) {
        esUpdated = String.valueOf(doc.get("updated_at"));
      }
    } catch (Exception ex) {
      esUpdated = null;
    }
    String pgUpdated = order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : null;
    return new OrderSyncStatus(orderId, pgUpdated, esUpdated,
        esUpdated != null && esUpdated.equals(pgUpdated));
  }
}
