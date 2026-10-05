package com.ecom.level3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ecom.level3.dto.Dtos.OrderItemOut;
import com.ecom.level3.dto.Dtos.OrderOut;
import com.ecom.level3.model.Level3Order;
import com.ecom.level3.model.Level3OrderItem;
import com.ecom.level3.model.Level3User;
import com.ecom.level3.service.OrderService;

/** ES/PG document is built FROM POSTGRESQL ONLY — catalog renames never rewrite history. */
class OrderDocumentTest {

  private static Level3Order order() {
    Level3User user = new Level3User("Wendy Wireless", "wendy.wireless@example.com");
    user.setId(1L);
    Level3Order order = new Level3Order();
    order.setId(7L);
    order.setUser(user);
    order.setStatus("PENDING");
    order.setTotalAmount(new BigDecimal("24.99"));
    order.setOrderDate(Instant.parse("2026-09-01T10:00:00Z"));
    order.setUpdatedAt(Instant.parse("2026-09-01T12:00:00Z"));
    Level3OrderItem item = new Level3OrderItem();
    item.setId(3L);
    item.setOrder(order);
    item.setProductId("m1");
    item.setTitle("Wireless Mouse");
    item.setQuantity(1);
    item.setUnitPrice(new BigDecimal("24.99"));
    order.setItems(new java.util.ArrayList<>(List.of(item)));
    return order;
  }

  @Test
  void documentSnapshotsTitleAndPrice() {
    Map<String, Object> doc = OrderService.toOrderDocument(order());
    assertEquals(7L, doc.get("order_id"));
    assertEquals("PENDING", doc.get("status"));
    assertEquals(24.99, doc.get("total_amount"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) doc.get("items");
    assertEquals("Wireless Mouse", items.get(0).get("title"));
    assertEquals(24.99, items.get(0).get("unit_price"));
    @SuppressWarnings("unchecked")
    Map<String, Object> customer = (Map<String, Object>) doc.get("customer");
    assertEquals("Wendy Wireless", customer.get("name"));
  }

  @Test
  void orderOutHasLineTotalsAndCustomerFields() {
    OrderOut out = OrderService.toOrderOut(order());
    assertEquals(7L, out.id());
    assertEquals(1L, out.userId());
    assertEquals("Wendy Wireless", out.customerName());
    assertEquals("wendy.wireless@example.com", out.customerEmail());
    OrderItemOut item = out.items().get(0);
    assertEquals("Wireless Mouse", item.title());
    assertEquals(24.99, item.lineTotal());
  }
}
