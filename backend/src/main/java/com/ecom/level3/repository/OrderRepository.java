package com.ecom.level3.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ecom.level3.model.Level3Order;

public interface OrderRepository extends JpaRepository<Level3Order, Long> {

  List<Level3Order> findByUpdatedAtGreaterThanEqualOrderByIdDesc(Instant cutoff, Pageable pageable);

  @Query("select o from Level3Order o left join fetch o.items left join fetch o.user where o.id = :id")
  java.util.Optional<Level3Order> findByIdWithItems(Long id);

  Page<Level3Order> findByUser_Id(Long userId, Pageable pageable);

  Page<Level3Order> findByStatus(String status, Pageable pageable);

  Page<Level3Order> findByUser_IdAndStatus(Long userId, String status, Pageable pageable);

  long countByStatus(String status);
}
