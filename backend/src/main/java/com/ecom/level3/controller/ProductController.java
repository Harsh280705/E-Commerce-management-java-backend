package com.ecom.level3.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ecom.level3.dto.Dtos.ProductIn;
import com.ecom.level3.dto.Dtos.ProductOut;
import com.ecom.level3.service.ProductService;

import jakarta.validation.Valid;

/** Product endpoints — MongoDB ONLY. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

  private final ProductService products;

  public ProductController(ProductService products) {
    this.products = products;
  }

  /** Storefront catalog (active only) or full catalog for admin. */
  @GetMapping
  public List<ProductOut> listProducts(@RequestParam(name = "all", defaultValue = "false") boolean all) {
    return products.listProducts(all);
  }

  @GetMapping("/{productId}")
  public ProductOut getProduct(@PathVariable String productId) {
    return products.getProduct(productId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ProductOut createProduct(@Valid @RequestBody ProductIn payload) {
    return products.createProduct(payload);
  }

  /** Catalog edits only — historical order_items and ES docs are untouched. */
  @PutMapping("/{productId}")
  public ProductOut updateProduct(@PathVariable String productId, @Valid @RequestBody ProductIn payload) {
    return products.updateProduct(productId, payload);
  }
}
