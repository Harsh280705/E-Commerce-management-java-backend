package com.ecom.level3.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.level3.model.Level3OrderItem;

public interface OrderItemRepository extends JpaRepository<Level3OrderItem, Long> {
}
