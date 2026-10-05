package com.ecom.level3.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ecom.level3.dto.Dtos.OrderSearchRequest;
import com.ecom.level3.exception.UnprocessableException;
import com.ecom.level3.service.OrderStatuses;
import com.ecom.level3.service.SearchService;

/** Admin search endpoint — Elasticsearch ONLY (never PostgreSQL/MongoDB). */
@RestController
@RequestMapping("/api/search")
public class SearchController {

  private static final Logger log = LoggerFactory.getLogger(SearchController.class);

  private final SearchService search;

  public SearchController(SearchService search) {
    this.search = search;
  }

  /** Translate UI filters into an ES bool/multi_match/range/agg query. */
  @PostMapping("/orders")
  public Map<String, Object> searchOrdersPost(@RequestBody(required = false) OrderSearchRequest payload) {
    OrderSearchRequest req = payload != null ? payload
        : new OrderSearchRequest(null, null, null, null, null, null, null, null, null);
    List<String> statuses = OrderStatuses.requireValidList(req.statuses());
    String sort = req.sort() != null ? req.sort() : "newest";
    if (!SearchService.SORT_MAP.containsKey(sort)) {
      throw new UnprocessableException("sort must be one of [newest, oldest, total_asc, total_desc]");
    }
    int page = req.page() != null ? req.page() : 1;
    int size = req.size() != null ? req.size() : 20;
    if (page < 1) {
      throw new UnprocessableException("page must be >= 1");
    }
    if (size < 1 || size > 100) {
      throw new UnprocessableException("size must be between 1 and 100");
    }
    if (req.minTotal() != null && req.minTotal() < 0) {
      throw new UnprocessableException("min_total must be >= 0");
    }
    if (req.maxTotal() != null && req.maxTotal() < 0) {
      throw new UnprocessableException("max_total must be >= 0");
    }
    try {
      return search.searchOrders(req.q(), statuses, req.dateFrom(), req.dateTo(),
          req.minTotal(), req.maxTotal(), sort, page, size);
    } catch (IllegalStateException ex) {
      // ES down -> 503; PostgreSQL data unaffected
      log.error("Elasticsearch search failed", ex);
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Search unavailable: " + ex.getMessage());
    }
  }
}
