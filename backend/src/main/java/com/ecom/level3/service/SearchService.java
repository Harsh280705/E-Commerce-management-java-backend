package com.ecom.level3.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.ecom.level3.search.ElasticOrderIndex;

/** Elasticsearch-backed admin order search (search path only). */
@Service
public class SearchService {

  public static final Map<String, List<Map<String, Object>>> SORT_MAP = Map.of(
      "newest", List.of(Map.of("order_date", Map.of("order", "desc"))),
      "oldest", List.of(Map.of("order_date", Map.of("order", "asc"))),
      "total_desc", List.of(Map.of("total_amount", Map.of("order", "desc"))),
      "total_asc", List.of(Map.of("total_amount", Map.of("order", "asc"))));

  private final ElasticOrderIndex es;

  public SearchService(ElasticOrderIndex es) {
    this.es = es;
  }

  /** Translate UI filters into an ES bool/multi_match/range/agg query. */
  public static Map<String, Object> buildEsQuery(String q, List<String> statuses,
      String dateFrom, String dateTo, Double minTotal, Double maxTotal) {
    List<Map<String, Object>> must = new ArrayList<>();
    List<Map<String, Object>> filters = new ArrayList<>();

    if (q != null && !q.isBlank()) {
      String term = q.strip();
      String digits = term.replaceAll("\\D", "");
      if (!digits.isEmpty() && (term.matches("\\d+") || term.toUpperCase().startsWith("ORD"))) {
        try {
          filters.add(Map.of("term", Map.of("order_id", Integer.parseInt(digits))));
        } catch (NumberFormatException ignored) {
        }
      } else {
        must.add(Map.of("multi_match", Map.of(
            "query", term,
            "fields", List.of("customer.name^3", "customer.email^2", "items.title^2"))));
      }
    }

    if (statuses != null && !statuses.isEmpty()) {
      if (statuses.size() == 1) {
        filters.add(Map.of("term", Map.of("status", statuses.get(0))));
      } else {
        filters.add(Map.of("terms", Map.of("status", statuses)));
      }
    }

    Map<String, Object> dateRange = new LinkedHashMap<>();
    if (dateFrom != null && !dateFrom.isBlank()) {
      dateRange.put("gte", dateFrom.strip());
    }
    if (dateTo != null && !dateTo.isBlank()) {
      dateRange.put("lte", dateTo.strip());
    }
    if (!dateRange.isEmpty()) {
      filters.add(Map.of("range", Map.of("order_date", dateRange)));
    }

    Map<String, Object> totalRange = new LinkedHashMap<>();
    if (minTotal != null) {
      totalRange.put("gte", minTotal.doubleValue());
    }
    if (maxTotal != null) {
      totalRange.put("lte", maxTotal.doubleValue());
    }
    if (!totalRange.isEmpty()) {
      filters.add(Map.of("range", Map.of("total_amount", totalRange)));
    }

    if (must.isEmpty() && filters.isEmpty()) {
      return Map.of("match_all", Map.of());
    }
    Map<String, Object> bool = new LinkedHashMap<>();
    if (!must.isEmpty()) {
      bool.put("must", must);
    }
    if (!filters.isEmpty()) {
      bool.put("filter", filters);
    }
    return Map.of("bool", bool);
  }

  public Map<String, Object> searchOrders(String q, List<String> statuses,
      String dateFrom, String dateTo, Double minTotal, Double maxTotal,
      String sort, int page, int size) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("query", buildEsQuery(q, statuses, dateFrom, dateTo, minTotal, maxTotal));
    body.put("sort", SORT_MAP.getOrDefault(sort, SORT_MAP.get("newest")));
    body.put("from", (page - 1) * size);
    body.put("size", size);
    body.put("aggs", Map.of(
        "total_revenue", Map.of("sum", Map.of("field", "total_amount")),
        "by_status", Map.of("terms", Map.of("field", "status"))));
    body.put("track_total_hits", true);

    Map<String, Object> resp = es.search(body);
    Map<String, Object> hits = asMap(resp.get("hits"));
    List<Map<String, Object>> results = new ArrayList<>();
    Object hitsList = hits.get("hits");
    if (hitsList instanceof List<?> list) {
      for (Object h : list) {
        if (h instanceof Map<?, ?> hit) {
          Object src = hit.get("_source");
          if (src instanceof Map<?, ?> srcMap) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : srcMap.entrySet()) {
              row.put(String.valueOf(e.getKey()), e.getValue());
            }
            row.put("score", hit.get("_score"));
            results.add(row);
          }
        }
      }
    }
    int total = 0;
    Object totalObj = hits.get("total");
    if (totalObj instanceof Map<?, ?> t) {
      Object value = t.get("value");
      if (value instanceof Number n) {
        total = n.intValue();
      }
    }
    Map<String, Object> aggs = asMap(resp.get("aggregations"));
    double revenue = 0.0;
    Object revenueObj = asMap(aggs.get("total_revenue")).get("value");
    if (revenueObj instanceof Number n) {
      revenue = n.doubleValue();
    }
    Map<String, Integer> byStatus = new LinkedHashMap<>();
    Object buckets = asMap(aggs.get("by_status")).get("buckets");
    if (buckets instanceof List<?> list) {
      for (Object b : list) {
        if (b instanceof Map<?, ?> bucket) {
          Object key = bucket.get("key");
          Object count = bucket.get("doc_count");
          if (key != null && count instanceof Number n) {
            byStatus.put(String.valueOf(key), n.intValue());
          }
        }
      }
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("total", total);
    out.put("page", page);
    out.put("size", size);
    out.put("results", results);
    out.put("aggs", Map.of("total_revenue", revenue, "by_status", byStatus));
    return out;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object o) {
    if (o instanceof Map<?, ?> m) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> e : m.entrySet()) {
        out.put(String.valueOf(e.getKey()), e.getValue());
      }
      return out;
    }
    return Map.of();
  }
}
