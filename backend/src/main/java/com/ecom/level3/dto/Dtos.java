package com.ecom.level3.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** API DTOs — Jackson serializes snake_case globally (see application.properties). */
public final class Dtos {
  private Dtos() {}

  // ---------- Users ----------
  public record UserOut(long id, String name, String email) {}

  // ---------- Products ----------
  public record VariantDto(String name, String sku, double priceAdjustment) {}

  public record ProductIn(
      @NotBlank @Size(max = 64) String sku,
      @NotBlank @Size(max = 255) String title,
      String description,
      @Positive double price,
      String category,
      List<String> tags,
      Map<String, Object> attributes,
      List<VariantDto> variants,
      Boolean active) {}

  public record ProductOut(
      String id, String sku, String title, String description, double price,
      String category, List<String> tags, Map<String, Object> attributes,
      List<VariantDto> variants, boolean active, Instant updatedAt) {}

  // ---------- Orders ----------
  public record OrderItemIn(
      @NotBlank String productId,
      @Min(1) @Max(1000) int quantity) {}

  public record OrderCreate(
      @NotNull Long userId,
      @NotNull @Size(min = 1) List<@Valid OrderItemIn> items) {}

  public record OrderItemOut(
      long id, String productId, String title, int quantity, double unitPrice, double lineTotal) {}

  public record OrderOut(
      long id, long userId, String customerName, String customerEmail,
      Instant orderDate, String status, double totalAmount,
      Instant updatedAt, List<OrderItemOut> items) {}

  public record OrderStatusUpdate(String status) {}

  public record OrderSyncStatus(
      long orderId, String pgUpdatedAt, String esUpdatedAt, boolean inSync) {}

  // ---------- Admin search ----------
  public record OrderSearchRequest(
      String q,
      List<String> statuses,
      String dateFrom,
      String dateTo,
      Double minTotal,
      Double maxTotal,
      String sort,
      Integer page,
      Integer size) {}
}
