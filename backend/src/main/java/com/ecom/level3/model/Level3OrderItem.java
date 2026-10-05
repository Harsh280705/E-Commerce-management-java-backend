package com.ecom.level3.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * PostgreSQL order items — Level 3 table.
 * Carries a denormalized snapshot (title + unit_price) so historical orders
 * never depend on the live MongoDB catalog.
 */
@Entity
@Table(name = "level3_order_items")
public class Level3OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Level3Order order;

  /** MongoDB product id as string (no cross-database FK possible). */
  @Column(name = "product_id", nullable = false, length = 64)
  private String productId;

  @Column(nullable = false, length = 255)
  private String title = "";

  @Column(nullable = false)
  private Integer quantity;

  @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal unitPrice;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Level3Order getOrder() { return order; }
  public void setOrder(Level3Order order) { this.order = order; }
  public String getProductId() { return productId; }
  public void setProductId(String productId) { this.productId = productId; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public Integer getQuantity() { return quantity; }
  public void setQuantity(Integer quantity) { this.quantity = quantity; }
  public BigDecimal getUnitPrice() { return unitPrice; }
  public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}
