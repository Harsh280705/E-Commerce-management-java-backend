package com.ecom.level3;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableRabbit
@EnableRetry
@EnableScheduling
public class Level3Application {
  public static void main(String[] args) {
    SpringApplication.run(Level3Application.class, args);
  }
}
