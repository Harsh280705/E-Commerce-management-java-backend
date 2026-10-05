package com.ecom.level3.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.ecom.level3.dto.Dtos.ProductIn;
import com.ecom.level3.dto.Dtos.ProductOut;
import com.ecom.level3.dto.Dtos.VariantDto;
import com.ecom.level3.exception.ConflictException;
import com.ecom.level3.exception.NotFoundException;
import com.ecom.level3.model.Product;
import com.ecom.level3.repository.ProductRepository;

/** MongoDB product catalog — the ONLY product data-access layer. */
@Service
public class ProductService {

  private final ProductRepository products;

  public ProductService(ProductRepository products) {
    this.products = products;
  }

  public List<ProductOut> listProducts(boolean all) {
    List<Product> docs = all ? products.findAll() : products.findByActiveTrue();
    return docs.stream().map(ProductService::toOut).toList();
  }

  public ProductOut getProduct(String id) {
    Product p = products.findById(id).orElseThrow(() -> new NotFoundException("Product not found"));
    return toOut(p);
  }

  /** Return {requestedId: product} for all found ids. */
  public Map<String, Product> lookupByIds(List<String> ids) {
    Map<String, Product> found = new HashMap<>();
    for (Product p : products.findByIdIn(new ArrayList<>(new java.util.HashSet<>(ids)))) {
      if (p.getId() != null) {
        found.put(p.getId(), p);
      }
    }
    Map<String, Product> result = new LinkedHashMap<>();
    for (String id : ids) {
      if (found.containsKey(id)) {
        result.put(id, found.get(id));
      }
    }
    return result;
  }

  public ProductOut createProduct(ProductIn in) {
    products.findBySku(in.sku()).ifPresent(p -> {
      throw new ConflictException("sku already exists: " + in.sku());
    });
    Product p = new Product();
    apply(p, in);
    p.setUpdatedAt(Instant.now());
    try {
      p = products.save(p);
    } catch (DuplicateKeyException ex) {
      throw new ConflictException("sku already exists: " + in.sku());
    }
    return toOut(p);
  }

  public ProductOut updateProduct(String id, ProductIn in) {
    Product p = products.findById(id).orElseThrow(() -> new NotFoundException("Product not found"));
    if (in.sku() != null && !in.sku().equals(p.getSku())) {
      products.findBySku(in.sku()).ifPresent(other -> {
        throw new ConflictException("sku already exists: " + in.sku());
      });
    }
    apply(p, in);
    p.setUpdatedAt(Instant.now());
    try {
      p = products.save(p);
    } catch (DuplicateKeyException ex) {
      throw new ConflictException("sku already exists: " + in.sku());
    }
    return toOut(p);
  }

  private static void apply(Product p, ProductIn in) {
    if (in.sku() != null) {
      p.setSku(in.sku());
    }
    if (in.title() != null) {
      p.setTitle(in.title());
    }
    if (in.description() != null) {
      p.setDescription(in.description());
    }
    p.setPrice(in.price());
    if (in.category() != null) {
      p.setCategory(in.category());
    }
    if (in.tags() != null) {
      p.setTags(new ArrayList<>(in.tags()));
    }
    if (in.attributes() != null) {
      p.setAttributes(new HashMap<>(in.attributes()));
    }
    if (in.variants() != null) {
      List<Product.Variant> variants = new ArrayList<>();
      for (VariantDto v : in.variants()) {
        Product.Variant variant = new Product.Variant();
        variant.setName(v.name() != null ? v.name() : "");
        variant.setSku(v.sku() != null ? v.sku() : "");
        variant.setPriceAdjustment(v.priceAdjustment());
        variants.add(variant);
      }
      p.setVariants(variants);
    }
    if (in.active() != null) {
      p.setActive(in.active());
    }
  }

  public static ProductOut toOut(Product p) {
    List<VariantDto> variants = new ArrayList<>();
    if (p.getVariants() != null) {
      for (Product.Variant v : p.getVariants()) {
        variants.add(new VariantDto(v.getName(), v.getSku(), v.getPriceAdjustment()));
      }
    }
    return new ProductOut(p.getId(), p.getSku() != null ? p.getSku() : "",
        p.getTitle() != null ? p.getTitle() : "",
        p.getDescription() != null ? p.getDescription() : "",
        p.getPrice(), p.getCategory() != null ? p.getCategory() : "",
        p.getTags() != null ? p.getTags() : List.of(),
        p.getAttributes() != null ? p.getAttributes() : Map.of(),
        variants, p.isActive(), p.getUpdatedAt());
  }
}
