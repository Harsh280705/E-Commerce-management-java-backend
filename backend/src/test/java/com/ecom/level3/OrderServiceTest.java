package com.ecom.level3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import com.ecom.level3.dto.Dtos.OrderCreate;
import com.ecom.level3.dto.Dtos.OrderItemIn;
import com.ecom.level3.dto.Dtos.OrderOut;
import com.ecom.level3.exception.NotFoundException;
import com.ecom.level3.exception.UnprocessableException;
import com.ecom.level3.model.Level3User;
import com.ecom.level3.model.Product;
import com.ecom.level3.repository.OrderRepository;
import com.ecom.level3.repository.UserRepository;
import com.ecom.level3.service.OrderService;
import com.ecom.level3.service.ProductService;

/**
 * Order write path against H2 (MongoDB + publisher mocked).
 * Mirrors the Level 2 Python tests: snapshot totals, rollback on bad input,
 * inactive rejection, snapshot rename immunity, status updates, listing.
 */
@DataJpaTest
@Import(OrderService.class)
class OrderServiceTest {

  @Autowired
  private OrderRepository orders;

  @Autowired
  private UserRepository users;

  @Autowired
  private OrderService service;

  @MockBean
  private ProductService products;

  private Product mouse;
  private Product keyboard;
  private Product dead;

  @BeforeEach
  void setUp() {
    users.save(new Level3User("Wendy Wireless", "wendy.wireless@example.com"));
    users.save(new Level3User("John Doe", "john.doe@example.com"));

    mouse = product("m1", "PER-001", "Wireless Mouse", 24.99, true);
    keyboard = product("k1", "PER-002", "Mechanical Keyboard", 89.99, true);
    dead = product("d1", "X-1", "Old", 5.0, false);

    when(products.lookupByIds(anyList())).thenAnswer(inv -> {
      @SuppressWarnings("unchecked")
      List<String> ids = (List<String>) inv.getArgument(0);
      Map<String, Product> all = Map.of("m1", mouse, "k1", keyboard, "d1", dead);
      Map<String, Product> out = new java.util.LinkedHashMap<>();
      for (String id : ids) {
        if (all.containsKey(id)) {
          out.put(id, all.get(id));
        }
      }
      return out;
    });
  }

  private static Product product(String id, String sku, String title, double price, boolean active) {
    Product p = new Product();
    p.setId(id);
    p.setSku(sku);
    p.setTitle(title);
    p.setPrice(price);
    p.setActive(active);
    return p;
  }

  private long wendyId() {
    return users.findAll().stream()
        .filter(u -> u.getEmail().equals("wendy.wireless@example.com"))
        .findFirst().orElseThrow().getId();
  }

  @Test
  void orderSnapshotsTitleAndPriceAndTotal() {
    OrderOut out = OrderService.toOrderOut(service.createOrder(new OrderCreate(wendyId(),
        List.of(new OrderItemIn("m1", 2), new OrderItemIn("k1", 1)))));
    assertEquals("PENDING", out.status());
    assertEquals(Math.round((24.99 * 2 + 89.99) * 100.0) / 100.0, out.totalAmount());
    assertEquals("Wireless Mouse", out.items().get(0).title());
  }

  @Test
  void orderRejectsMissingInactiveAndDuplicateProducts() {
    long uid = wendyId();
    assertThrows(NotFoundException.class, () ->
        service.createOrder(new OrderCreate(uid, List.of(new OrderItemIn("nope", 1)))));
    assertThrows(UnprocessableException.class, () ->
        service.createOrder(new OrderCreate(uid, List.of(new OrderItemIn("d1", 1)))));
    assertThrows(UnprocessableException.class, () ->
        service.createOrder(new OrderCreate(uid,
            List.of(new OrderItemIn("m1", 1), new OrderItemIn("m1", 1)))));
    assertThrows(NotFoundException.class, () ->
        service.createOrder(new OrderCreate(9999L, List.of(new OrderItemIn("m1", 1)))));
    assertEquals(0, orders.count());
  }

  @Test
  void catalogRenameDoesNotRewriteHistory() {
    OrderOut out = OrderService.toOrderOut(service.createOrder(
        new OrderCreate(wendyId(), List.of(new OrderItemIn("m1", 1)))));
    mouse.setTitle("Wireless Mouse Pro");
    mouse.setPrice(34.99);
    Map<String, Object> doc = OrderService.toOrderOut(service.getOrderOrThrow(out.id())) != null
        ? docOf(out.id()) : null;
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) doc.get("items");
    assertEquals("Wireless Mouse", items.get(0).get("title"));
    assertEquals(24.99, items.get(0).get("unit_price"));
    assertEquals(24.99, doc.get("total_amount"));
  }

  private Map<String, Object> docOf(long orderId) {
    return OrderService.toOrderDocument(service.getOrderOrThrow(orderId));
  }

  @Test
  void statusUpdateAndInvalidStatus() {
    OrderOut out = OrderService.toOrderOut(service.createOrder(
        new OrderCreate(wendyId(), List.of(new OrderItemIn("m1", 1)))));
    assertEquals("SHIPPED", service.updateStatus(out.id(), "shipped").getStatus());
    assertThrows(UnprocessableException.class, () -> service.updateStatus(out.id(), "bogus"));
    assertThrows(NotFoundException.class, () -> service.updateStatus(9999L, "SHIPPED"));
  }

  @Test
  void listOrdersByUserStatusAndPagination() {
    long wendy = wendyId();
    long john = users.findAll().stream()
        .filter(u -> u.getEmail().equals("john.doe@example.com"))
        .findFirst().orElseThrow().getId();
    OrderOut a = OrderService.toOrderOut(
        service.createOrder(new OrderCreate(wendy, List.of(new OrderItemIn("m1", 1)))));
    OrderOut b = OrderService.toOrderOut(
        service.createOrder(new OrderCreate(john, List.of(new OrderItemIn("k1", 1)))));
    service.updateStatus(b.id(), "SHIPPED");

    var byUser = service.listOrders(wendy, null, 1, 20);
    assertEquals(1, byUser.getTotalElements());
    assertEquals(a.id(), byUser.getContent().get(0).getId());

    var byStatus = service.listOrders(null, "SHIPPED", 1, 20);
    assertEquals(1, byStatus.getTotalElements());
    assertEquals(b.id(), byStatus.getContent().get(0).getId());

    var page1 = service.listOrders(null, null, 1, 1);
    assertEquals(1, page1.getContent().size());
    assertEquals(b.id(), page1.getContent().get(0).getId()); // newest first
    assertTrue(page1.getTotalElements() == 2);
  }
}
