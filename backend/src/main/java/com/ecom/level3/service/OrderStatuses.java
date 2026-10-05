package com.ecom.level3.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ecom.level3.exception.UnprocessableException;

/** Canonical order statuses (exact uppercase values, same as Level 2). */
public final class OrderStatuses {

  public static final List<String> ALL = List.of("PENDING", "PROCESSING", "SHIPPED");
  private static final Set<String> SET = new HashSet<>(Arrays.asList("PENDING", "PROCESSING", "SHIPPED"));

  public static String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }

  public static boolean isValid(String value) {
    return SET.contains(normalize(value));
  }

  public static String requireValid(String value) {
    String norm = normalize(value);
    if (!SET.contains(norm)) {
      throw new UnprocessableException("status must be one of " + ALL);
    }
    return norm;
  }

  public static List<String> requireValidList(List<String> values) {
    if (values == null) {
      return null;
    }
    List<String> normed = values.stream()
        .map(OrderStatuses::normalize)
        .filter(s -> !s.isEmpty())
        .toList();
    List<String> bad = normed.stream().filter(s -> !SET.contains(s)).toList();
    if (!bad.isEmpty()) {
      throw new UnprocessableException("statuses must be subset of " + ALL);
    }
    return normed;
  }
}
