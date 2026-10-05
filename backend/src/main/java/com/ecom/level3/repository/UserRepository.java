package com.ecom.level3.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.level3.model.Level3User;

public interface UserRepository extends JpaRepository<Level3User, Long> {
  Optional<Level3User> findByEmail(String email);
}
