package com.ecom.level3.search;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Minimal Elasticsearch HTTP client (search projection only).
 * Uses the plain REST API so no ES client library version coupling exists.
 * Index is always the isolated Level 3 index (e.g. {@code level3_orders}).
 */
@Component
public class ElasticOrderIndex {

  private static final Logger log = LoggerFactory.getLogger(ElasticOrderIndex.class);

  private static final Map<String, Object> ORDER_INDEX_MAPPING = Map.of(
      "mappings", Map.of(
          "properties", Map.of(
              "order_id", Map.of("type", "integer"),
              "order_date", Map.of("type", "date"),
              "status", Map.of("type", "keyword"),
              "total_amount", Map.of("type", "float"),
              "updated_at", Map.of("type", "date"),
              "customer", Map.of("properties", Map.of(
                  "id", Map.of("type", "integer"),
                  "name", Map.of("type", "text", "fields",
                      Map.of("keyword", Map.of("type", "keyword", "ignore_above", 256))),
                  "email", Map.of("type", "text", "fields",
                      Map.of("keyword", Map.of("type", "keyword", "ignore_above", 256))))),
              "items", Map.of("type", "nested", "properties", Map.of(
                  "product_id", Map.of("type", "keyword"),
                  "title", Map.of("type", "text", "fields",
                      Map.of("keyword", Map.of("type", "keyword", "ignore_above", 256))),
                  "quantity", Map.of("type", "integer"),
                  "unit_price", Map.of("type", "float"))))));

  private final RestTemplate restTemplate;
  private final String esUrl;
  private final String index;

  public ElasticOrderIndex(RestTemplate restTemplate,
      @Value("${app.elasticsearch.url:http://localhost:9211}") String esUrl,
      @Value("${app.elasticsearch.index:level3_orders}") String index) {
    this.restTemplate = restTemplate;
    this.esUrl = esUrl;
    this.index = index;
  }

  public String getIndex() { return index; }

  public boolean indexExists() {
    try {
      ResponseEntity<String> resp = restTemplate.exchange(
          esUrl + "/" + index, HttpMethod.HEAD, null, String.class);
      return resp.getStatusCode().is2xxSuccessful();
    } catch (Exception ex) {
      if (ex.getMessage() != null && ex.getMessage().contains("404")) {
        return false;
      }
      throw new IllegalStateException("Elasticsearch unavailable: " + ex.getMessage(), ex);
    }
  }

  /** Create the orders index if missing (idempotent). */
  public void ensureIndex() {
    try {
      if (indexExists()) {
        return;
      }
    } catch (IllegalStateException ex) {
      throw ex;
    }
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    try {
      restTemplate.exchange(esUrl + "/" + index, HttpMethod.PUT,
          new HttpEntity<>(ORDER_INDEX_MAPPING, headers), Map.class);
      log.info("Created Elasticsearch index {}", index);
    } catch (Exception ex) {
      throw new IllegalStateException("Elasticsearch unavailable: " + ex.getMessage(), ex);
    }
  }

  public void deleteIndex() {
    try {
      restTemplate.exchange(esUrl + "/" + index, HttpMethod.DELETE, null, Map.class);
    } catch (Exception ex) {
      if (ex.getMessage() == null || !ex.getMessage().contains("404")) {
        throw new IllegalStateException("Elasticsearch unavailable: " + ex.getMessage(), ex);
      }
    }
  }

  /** Idempotent upsert — document id = PostgreSQL order id. */
  public void indexDocument(long orderId, Map<String, Object> document) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    try {
      restTemplate.exchange(esUrl + "/" + index + "/_doc/" + orderId, HttpMethod.PUT,
          new HttpEntity<>(document, headers), Map.class);
    } catch (Exception ex) {
      throw new IllegalStateException("Elasticsearch unavailable: " + ex.getMessage(), ex);
    }
  }

  /** Bulk upsert via _bulk API (idempotent, one action per order). */
  public void bulkIndex(Map<Long, Map<String, Object>> documents) {
    if (documents.isEmpty()) {
      return;
    }
    StringBuilder ndjson = new StringBuilder();
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      for (Map.Entry<Long, Map<String, Object>> e : documents.entrySet()) {
        Map<String, Object> action = Map.of("index",
            Map.of("_index", index, "_id", String.valueOf(e.getKey())));
        ndjson.append(mapper.writeValueAsString(action)).append('\n');
        ndjson.append(mapper.writeValueAsString(e.getValue())).append('\n');
      }
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to encode bulk request: " + ex.getMessage(), ex);
    }
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/x-ndjson"));
    try {
      ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
          esUrl + "/_bulk", HttpMethod.POST, new HttpEntity<>(ndjson.toString(), headers),
          new ParameterizedTypeReference<Map<String, Object>>() {});
      Map<String, Object> body = resp.getBody();
      if (body != null && Boolean.TRUE.equals(body.get("errors"))) {
        log.warn("Bulk index reported item errors for index {}", index);
      }
    } catch (Exception ex) {
      throw new IllegalStateException("Elasticsearch unavailable: " + ex.getMessage(), ex);
    }
  }

  public void refresh() {
    try {
      restTemplate.exchange(esUrl + "/" + index + "/_refresh", HttpMethod.POST,
          HttpEntity.EMPTY, Map.class);
    } catch (Exception ex) {
      log.warn("Index refresh failed: {}", ex.getMessage());
    }
  }

  /** Fetch a single projected document (used by the sync-status endpoint). */
  public Map<String, Object> getDocument(long orderId) {
    try {
      ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
          esUrl + "/" + index + "/_doc/" + orderId, HttpMethod.GET, null,
          new ParameterizedTypeReference<Map<String, Object>>() {});
      if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
        Object found = resp.getBody().get("found");
        if (Boolean.FALSE.equals(found)) {
          return null;
        }
        Object source = resp.getBody().get("_source");
        if (source instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> doc = (Map<String, Object>) source;
          return doc;
        }
      }
      return null;
    } catch (Exception ex) {
      if (ex.getMessage() != null && ex.getMessage().contains("404")) {
        return null;
      }
      throw new IllegalStateException("Elasticsearch unavailable: " + ex.getMessage(), ex);
    }
  }

  public Map<String, Object> search(Map<String, Object> body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    try {
      ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
          esUrl + "/" + index + "/_search", HttpMethod.POST,
          new HttpEntity<>(body, headers),
          new ParameterizedTypeReference<Map<String, Object>>() {});
      return resp.getBody() != null ? resp.getBody() : Map.of();
    } catch (Exception ex) {
      throw new IllegalStateException("Elasticsearch unavailable: " + ex.getMessage(), ex);
    }
  }

  public long countDocs() {
    try {
      ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
          esUrl + "/" + index + "/_count", HttpMethod.GET, null,
          new ParameterizedTypeReference<Map<String, Object>>() {});
      Object count = resp.getBody() != null ? resp.getBody().get("count") : null;
      return count instanceof Number ? ((Number) count).longValue() : 0L;
    } catch (Exception ex) {
      return 0L;
    }
  }

  public static List<Map<String, Object>> emptyResults() {
    return new ArrayList<>();
  }

  public static Map<String, Object> emptySearchEnvelope() {
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("total", 0);
    return envelope;
  }
}
