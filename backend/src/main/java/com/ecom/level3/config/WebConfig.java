package com.ecom.level3.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
        .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173",
            "http://localhost:8080", "http://127.0.0.1:8080",
            "http://localhost", "http://127.0.0.1",
            "http://localhost:8082", "http://127.0.0.1:8082",
            "https://pedigree-silicon-levitate.ngrok-free.dev")
        .allowedMethods("*")
        .allowedHeaders("*")
        .allowCredentials(true);
  }
}
