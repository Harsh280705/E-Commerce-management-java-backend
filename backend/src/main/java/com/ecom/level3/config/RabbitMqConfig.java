package com.ecom.level3.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/** Level 3 RabbitMQ topology — isolated queue/exchange, never Level 2's. */
@Configuration
public class RabbitMqConfig {

  @Value("${app.sync.queue:level3_order_sync}")
  private String syncQueue;

  @Value("${app.sync.exchange:level3_orders}")
  private String syncExchange;

  @Value("${app.sync.max-retries:5}")
  private int maxRetries;

  @Bean
  public Queue orderSyncQueue() {
    return QueueBuilder.durable(syncQueue).build();
  }

  @Bean
  public DirectExchange orderSyncExchange() {
    return new DirectExchange(syncExchange, true, false);
  }

  @Bean
  public Binding orderSyncBinding(Queue orderSyncQueue, DirectExchange orderSyncExchange) {
    return BindingBuilder.bind(orderSyncQueue).to(orderSyncExchange).with(syncQueue);
  }

  @Bean
  public Jackson2JsonMessageConverter jacksonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
      Jackson2JsonMessageConverter converter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(converter);
    return template;
  }

  /** Listener factory with bounded retries + backoff (ES outages never lose the message silently). */
  @Bean
  public SimpleRabbitListenerContainerFactory syncListenerFactory(
      ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(converter);
    factory.setDefaultRequeueRejected(false);
    RetryOperationsInterceptor retry = RetryInterceptorBuilder.stateless()
        .maxAttempts(Math.max(1, maxRetries))
        .backOffOptions(8000, 2.0, 60000)
        .build();
    factory.setAdviceChain(retry);
    return factory;
  }
}
