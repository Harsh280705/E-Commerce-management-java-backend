package com.ecom.level3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import com.ecom.level3.model.Level3Order;
import com.ecom.level3.model.Level3OrderItem;
import com.ecom.level3.model.Level3User;
import com.ecom.level3.repository.OrderRepository;
import com.ecom.level3.repository.UserRepository;
import com.ecom.level3.search.ElasticOrderIndex;
import com.ecom.level3.service.SyncService;

/**
 * Sync strategies: idempotent dual-write upsert, ES-down retry surface
 * (PG never rolled back), polling bulk safety net.
 */
@DataJpaTest
@Import(SyncService.class)
class SyncServiceTest {

  @Autowired
  private OrderRepository orders;

  @Autowired
  private UserRepository users;

  @Autowired
  private SyncService sync;

  @MockBean
  private ElasticOrderIndex es;

  private long seedOrder() {
    Level3User user = users.save(new Level3User("Wendy Wireless", "wendy.wireless@example.com"));
    Level3Order order = new Level3Order();
    order.setUser(user);
    order.setStatus("PENDING");
    order.setTotalAmount(new java.math.BigDecimal("24.99"));
    order = orders.save(order);
    Level3OrderItem item = new Level3OrderItem();
    item.setOrder(order);
    item.setProductId("m1");
    item.setTitle("Wireless Mouse");
    item.setQuantity(1);
    item.setUnitPrice(new java.math.BigDecimal("24.99"));
    order.getItems().add(item);
    return orders.save(order).getId();
  }

  @Test
  void dualWriteUpsertIsIdempotent() {
    ReflectionTestUtils.setField(sync, "es", es);
    long id = seedOrder();
    Map<Long, Map<String, Object>> store = new java.util.LinkedHashMap<>();
    org.mockito.Mockito.doAnswer(inv -> {
      store.put((Long) inv.getArgument(0), inv.getArgument(1));
      return null;
    }).when(es).indexDocument(anyLong(), anyMap());

    assertTrue((Boolean) sync.upsertOrderDocument(id).get("synced"));
    assertTrue((Boolean) sync.upsertOrderDocument(id).get("synced"));
    assertEquals(List.of(id), List.copyOf(store.keySet()));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) store.get(id).get("items");
    assertEquals("Wireless Mouse", items.get(0).get("title"));
    verify(es, times(2)).indexDocument(anyLong(), anyMap());
  }

  @Test
  void esDownSurfacesRetryWithoutRollback() {
    ReflectionTestUtils.setField(sync, "es", es);
    long id = seedOrder();
    doThrow(new IllegalStateException("Elasticsearch unavailable: down"))
        .when(es).indexDocument(anyLong(), anyMap());
    assertThrows(IllegalStateException.class, () -> sync.upsertOrderDocument(id));
    assertEquals(1, orders.findById(id).stream().count());
  }

  @Test
  void pollingBulkSyncsChangedOrders() {
    long id = seedOrder();
    ElasticOrderIndex freshEs = mock(ElasticOrderIndex.class);
    java.util.List<Map<String, Object>> bulked = new java.util.ArrayList<>();
    org.mockito.Mockito.doAnswer(inv -> {
      @SuppressWarnings("unchecked")
      Map<Long, Map<String, Object>> docs = (Map<Long, Map<String, Object>>) inv.getArgument(0);
      bulked.addAll(docs.values());
      return null;
    }).when(freshEs).bulkIndex(any());
    ReflectionTestUtils.setField(sync, "es", freshEs);

    Map<String, Object> out = sync.pollChangedOrders("poll");
    @SuppressWarnings("unchecked")
    List<Long> synced = (List<Long>) out.get("synced");
    assertTrue(synced.contains(id), "expected " + id + " in " + synced);
  }
}
