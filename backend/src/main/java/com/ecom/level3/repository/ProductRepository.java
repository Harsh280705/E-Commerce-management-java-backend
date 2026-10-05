package com.ecom.level3.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.ecom.level3.model.Product;

public interface ProductRepository extends MongoRepository<Product, String> {
  List<Product> findByActiveTrue();

  List<Product> findByIdIn(List<String> ids);

  Optional<Product> findBySku(String sku);
}
