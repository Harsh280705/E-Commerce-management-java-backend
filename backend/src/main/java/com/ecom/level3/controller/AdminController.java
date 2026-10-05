package com.ecom.level3.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecom.level3.service.ReindexService;

/**
 * Level 3 administration. Rebuilds the Level 3 Elasticsearch index from
 * Level 3 PostgreSQL — never touches Level 2 data.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final ReindexService reindex;

  public AdminController(ReindexService reindex) {
    this.reindex = reindex;
  }

  @PostMapping("/reindex")
  public Map<String, Object> reindex() {
    return reindex.rebuildIndex();
  }
}
