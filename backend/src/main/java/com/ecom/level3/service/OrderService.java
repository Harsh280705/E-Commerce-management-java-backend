package com.ecom.level3.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.level3.dto.Dtos.OrderCreate;
import com.ecom.level3.dto.Dtos.OrderItemIn;
import com.ecom.level3.dto.Dtos.OrderItemOut;
import com.ecom.level3.dto.Dtos.OrderOut;
import com.ecom.level3.exception.NotFoundException;
import com.ecom.level3.exception.UnprocessableException;
import com.ecom.level3.model.Level3Order;
import com.ecom.level3.model.Level3OrderItem;
import com.ecom.level3.model.Level3User;
import com.ecom.level3.model.Product;
import com.ecom.level3.repository.OrderRepository;
import com.ecom.level3.repository.UserRepository;

/**
 * Order write/read path. PostgreSQL is the source of truth.
 * ES sync is published by the controller ONLY after these transactions commit.
 */
@Service
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);

  private final OrderRepository orders;
  private final UserRepository users;
  private final ProductService products;

  public OrderService(OrderRepository orders, UserRepository users, ProductService products) {
    this.orders = orders;
    this.users = users;
    this.products = products;
  }

  @Transactional
  public Level3Order createOrder(OrderCreate payload) {
    if (payload.items() == null || payload.items().isEmpty()) {
      throw new UnprocessableException("items must not be empty");
    }
    Set<String> seen = new HashSet<>();
    for (OrderItemIn item : payload.items()) {
      if (item.quantity() < 1 || item.quantity() > 1000) {
        throw new UnprocessableException("quantity must be between 1 and 1000");
      }
      if (!seen.add(item.productId())) {
        throw new UnprocessableException("Duplicate product_id in items");
      }
    }
    Level3User user = users.findById(payload.userId())
        .orElseThrow(() -> new NotFoundException("User not found"));

    List<String> ids = payload.items().stream().map(OrderItemIn::productId).toList();
    Map<String, Product> found = products.lookupByIds(ids);
    List<String> missing = ids.stream().filter(id -> !found.containsKey(id)).toList();
    if (!missing.isEmpty()) {
      throw new NotFoundException("Products not found: " + missing);
    }
    List<String> inactive = ids.stream()
        .filter(id -> !found.get(id).isActive())
        .toList();
    if (!inactive.isEmpty()) {
      throw new UnprocessableException("Products not for sale: " + inactive);
    }

    try {
      Level3Order order = new Level3Order();
      order.setUser(user);
      order.setStatus("PENDING");
      order.setTotalAmount(BigDecimal.ZERO);
      order = orders.save(order);

      BigDecimal total = BigDecimal.ZERO;
      List<Level3OrderItem> items = new ArrayList<>();
      for (OrderItemIn in : payload.items()) {
        Product p = found.get(in.productId());
        BigDecimal unit = BigDecimal.valueOf(p.getPrice()).setScale(2, RoundingMode.HALF_UP);
        total = total.add(unit.multiply(BigDecimal.valueOf(in.quantity())));
        Level3OrderItem item = new Level3OrderItem();
        item.setOrder(order);
        item.setProductId(in.productId());
        item.setTitle(p.getTitle());
        item.setQuantity(in.quantity());
        item.setUnitPrice(unit);
        items.add(item);
      }
      order.getItems().addAll(items);
      order.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
      order = orders.save(order);
      return orders.findByIdWithItems(order.getId()).orElse(order);
    } catch (NotFoundException | UnprocessableException ex) {
      throw ex;
    } catch (Exception ex) {
      log.error("Order transaction failed; rolled back", ex);
      throw new IllegalStateException("Order creation failed");
    }
  }

  @Transactional(readOnly = true)
  public Level3Order getOrderOrThrow(long orderId) {
    return orders.findByIdWithItems(orderId)
        .orElseThrow(() -> new NotFoundException("Order not found"));
  }

  @Transactional
  public Level3Order updateStatus(long orderId, String status) {
    String norm = OrderStatuses.requireValid(status);
    Level3Order order = orders.findById(orderId)
        .orElseThrow(() -> new NotFoundException("Order not found"));
    order.setStatus(norm);
    orders.save(order);
    return orders.findByIdWithItems(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
  }

  @Transactional(readOnly = true)
  public Page<Level3Order> listOrders(Long userId, String status, int page, int size) {
    Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
    if (userId != null && status != null) {
      return orders.findByUser_IdAndStatus(userId, status, pageable);
    }
    if (userId != null) {
      return orders.findByUser_Id(userId, pageable);
    }
    if (status != null) {
      return orders.findByStatus(status, pageable);
    }
    return orders.findAll(pageable);
  }

  /** Serialize a managed order (call inside a transaction so lazy associations load). */
  public static OrderOut toOrderOut(Level3Order order) {
    String customerName = "";
    String customerEmail = "";
    Long userId = null;
    try {
      if (order.getUser() != null) {
        customerName = order.getUser().getName() != null ? order.getUser().getName() : "";
        customerEmail = order.getUser().getEmail() != null ? order.getUser().getEmail() : "";
        userId = order.getUser().getId();
      }
    } catch (Exception ex) {
      userId = null;
    }
    List<OrderItemOut> items = new ArrayList<>();
    if (order.getItems() != null) {
      for (Level3OrderItem it : order.getItems()) {
        double unit = it.getUnitPrice() != null ? it.getUnitPrice().doubleValue() : 0.0;
        double line = Math.round(unit * it.getQuantity() * 100.0) / 100.0;
        items.add(new OrderItemOut(it.getId() != null ? it.getId() : 0,
            it.getProductId(), it.getTitle() != null ? it.getTitle() : "",
            it.getQuantity(), unit, line));
      }
    }
    return new OrderOut(order.getId(), userId != null ? userId : 0,
        customerName, customerEmail, order.getOrderDate(), order.getStatus(),
        order.getTotalAmount() != null ? order.getTotalAmount().doubleValue() : 0.0,
        order.getUpdatedAt(), items);
  }

  /** Build the flattened ES document FROM POSTGRESQL ONLY (never MongoDB). */
  public static Map<String, Object> toOrderDocument(Level3Order order) {
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("order_id", order.getId());
    doc.put("order_date", order.getOrderDate() != null ? order.getOrderDate().toString() : null);
    doc.put("status", order.getStatus());
    doc.put("total_amount", order.getTotalAmount() != null ? order.getTotalAmount().doubleValue() : 0.0);
    doc.put("updated_at", order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : null);
    String name = "";
    String email = "";
    try {
      if (order.getUser() != null) {
        name = order.getUser().getName() != null ? order.getUser().getName() : "";
        email = order.getUser().getEmail() != null ? order.getUser().getEmail() : "";
      }
    } catch (Exception ignored) {
    }
    Map<String, Object> customer = new LinkedHashMap<>();
    try {
      customer.put("id", order.getUser() != null ? order.getUser().getId() : null);
    } catch (Exception ignored) {
      customer.put("id", null);
    }
    customer.put("name", name);
    customer.put("email", email);
    doc.put("customer", customer);
    List<Map<String, Object>> items = new ArrayList<>();
    if (order.getItems() != null) {
      for (Level3OrderItem it : order.getItems()) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("product_id", it.getProductId());
        m.put("title", it.getTitle());
        m.put("quantity", it.getQuantity());
        m.put("unit_price", it.getUnitPrice() != null ? it.getUnitPrice().doubleValue() : 0.0);
        items.add(m);
      }
    }
    doc.put("items", items);
    return doc;
  }
}
