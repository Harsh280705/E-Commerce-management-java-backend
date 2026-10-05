package com.ecom.level3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ecom.level3.search.ElasticOrderIndex;
import com.ecom.level3.service.SearchService;

/** ES search translation: bool/multi_match/range/aggs (mirrors Level 2 test). */
class SearchQueryTest {

  @Test
  void buildQueryUsesMultiMatchTermsRangeAndAggs() {
    Map<String, Object> q = SearchService.buildEsQuery(
        "Wireless", List.of("PENDING", "SHIPPED"), "2026-01-01", "2026-12-31", 10.0, 200.0);
    String s = q.toString();
    assertTrue(s.contains("multi_match"), "expected multi_match, got " + s);
    assertTrue(s.contains("terms"), "expected terms, got " + s);
    assertTrue(s.contains("range"), "expected range, got " + s);
    assertTrue(q.containsKey("bool"), "expected bool, got " + s);
  }

  @Test
  void emptyQueryIsMatchAll() {
    assertEquals(Map.of("match_all", Map.of()),
        SearchService.buildEsQuery(null, null, null, null, null, null));
  }

  @Test
  void numericQueryBecomesOrderIdTerm() {
    Map<String, Object> q = SearchService.buildEsQuery("42", null, null, null, null, null);
    assertTrue(q.toString().contains("order_id"), "got " + q);
  }

  @Test
  @SuppressWarnings("unchecked")
  void searchOrdersParsesEnvelopeAndAggs() {
    ElasticOrderIndex es = mock(ElasticOrderIndex.class);
    when(es.search(any())).thenReturn(Map.of(
        "hits", Map.of("total", Map.of("value", 2), "hits", List.of()),
        "aggregations", Map.of(
            "total_revenue", Map.of("value", 50.0),
            "by_status", Map.of("buckets", List.of(Map.of("key", "PENDING", "doc_count", 2))))));
    SearchService service = new SearchService(es);
    Map<String, Object> out = service.searchOrders("Wireless", List.of("PENDING", "SHIPPED"),
        "2026-01-01", "2026-12-31", 10.0, 200.0, "newest", 1, 20);
    Map<String, Object> aggs = (Map<String, Object>) out.get("aggs");
    assertEquals(50.0, aggs.get("total_revenue"));
    assertEquals(Map.of("PENDING", 2), aggs.get("by_status"));
    assertEquals(2, out.get("total"));
  }

  @Test
  void sortMapCoversAllChoices() {
    for (String sort : List.of("newest", "oldest", "total_asc", "total_desc")) {
      assertTrue(SearchService.SORT_MAP.containsKey(sort));
    }
  }
}
