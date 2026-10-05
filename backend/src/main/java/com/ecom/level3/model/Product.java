package com.ecom.level3.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** MongoDB product catalog document — Level 3 database (collection "products"). */
@Document(collection = "products")
public class Product {

  @Id
  private String id;

  @Indexed(unique = true)
  private String sku;

  private String title;
  private String description;
  private double price;
  private String category;
  private List<String> tags = new ArrayList<>();
  private Map<String, Object> attributes = new HashMap<>();
  private List<Variant> variants = new ArrayList<>();
  private boolean active = true;

  @Field("updated_at")
  private Instant updatedAt;

  public static class Variant {
    private String name = "";
    private String sku = "";
    private double priceAdjustment = 0.0;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public double getPriceAdjustment() { return priceAdjustment; }
    public void setPriceAdjustment(double priceAdjustment) { this.priceAdjustment = priceAdjustment; }
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getSku() { return sku; }
  public void setSku(String sku) { this.sku = sku; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public double getPrice() { return price; }
  public void setPrice(double price) { this.price = price; }
  public String getCategory() { return category; }
  public void setCategory(String category) { this.category = category; }
  public List<String> getTags() { return tags; }
  public void setTags(List<String> tags) { this.tags = tags; }
  public Map<String, Object> getAttributes() { return attributes; }
  public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
  public List<Variant> getVariants() { return variants; }
  public void setVariants(List<Variant> variants) { this.variants = variants; }
  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
