package com.ecom.level3.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecom.level3.dto.Dtos.UserOut;
import com.ecom.level3.repository.UserRepository;

/** User endpoints — PostgreSQL users (seeded, no real auth). */
@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserRepository users;

  public UserController(UserRepository users) {
    this.users = users;
  }

  @GetMapping
  public List<UserOut> listUsers() {
    return users.findAll().stream()
        .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
        .map(u -> new UserOut(u.getId(), u.getName(), u.getEmail()))
        .toList();
  }
}
