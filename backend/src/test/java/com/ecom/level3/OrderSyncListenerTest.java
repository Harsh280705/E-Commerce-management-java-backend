package com.ecom.level3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.ecom.level3.messaging.OrderSyncListener;
import com.ecom.level3.service.SyncService;

/** Listener parses raw broker payloads (Long JSON, maps, strings). */
class OrderSyncListenerTest {

  private static Message raw(String body) {
    MessageProperties props = new MessageProperties();
    props.setContentType("application/json");
    return new Message(body.getBytes(StandardCharsets.UTF_8), props);
  }

  @Test
  void rawLongBodySyncs() {
    SyncService sync = mock(SyncService.class);
    when(sync.upsertOrderDocument(anyLong())).thenReturn(Map.of("order_id", 43L, "synced", true));
    new OrderSyncListener(sync).onOrderSync(raw("43"));
    verify(sync).upsertOrderDocument(43L);
  }

  @Test
  void convertedLongSyncs() {
    SyncService sync = mock(SyncService.class);
    when(sync.upsertOrderDocument(anyLong())).thenReturn(Map.of("order_id", 7L, "synced", true));
    new OrderSyncListener(sync).onOrderSync(7L);
    verify(sync).upsertOrderDocument(7L);
  }

  @Test
  void garbageIsIgnoredWithoutThrowing() {
    SyncService sync = mock(SyncService.class);
    new OrderSyncListener(sync).onOrderSync(raw("not-json{{{"));
    org.mockito.Mockito.verifyNoInteractions(sync);
  }

  @Test
  void unwrapAndToOrderId() {
    assertEquals(43L, OrderSyncListener.toOrderId(OrderSyncListener.unwrap(raw("43"))));
    assertEquals(9L, OrderSyncListener.toOrderId(Map.of("order_id", 9)));
    assertEquals(10L, OrderSyncListener.toOrderId(Map.of("orderId", 10)));
    assertEquals(11L, OrderSyncListener.toOrderId("11"));
    assertNull(OrderSyncListener.toOrderId(OrderSyncListener.unwrap(raw(""))));
  }
}
