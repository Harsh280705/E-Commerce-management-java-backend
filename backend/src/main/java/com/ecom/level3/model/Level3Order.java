package com.ecom.level3.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** PostgreSQL orders — Level 3 table, source of truth for transactions. */
@Entity
@Table(name = "level3_orders")
public class Level3Order {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private Level3User user;

  @CreationTimestamp
  @Column(name = "order_date", nullable = false, updatable = false)
  private Instant orderDate;

  @Column(nullable = false, length = 32)
  private String status = "PENDING";

  @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal totalAmount = BigDecimal.ZERO;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  private List<Level3OrderItem> items = new ArrayList<>();

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Level3User getUser() { return user; }
  public void setUser(Level3User user) { this.user = user; }
  public Long getUserId() { return user != null ? user.getId() : null; }
  public Instant getOrderDate() { return orderDate; }
  public void setOrderDate(Instant orderDate) { this.orderDate = orderDate; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public BigDecimal getTotalAmount() { return totalAmount; }
  public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public List<Level3OrderItem> getItems() { return items; }
  public void setItems(List<Level3OrderItem> items) { this.items = items; }
}
