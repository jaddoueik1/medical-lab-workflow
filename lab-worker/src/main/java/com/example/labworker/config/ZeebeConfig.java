package com.example.labworker.config;

import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZeebeConfig {

  @Bean
  public ZeebeClient zeebeClient() {
    String addr = System.getenv().getOrDefault("ZEEBE_CLIENT_BROKER_CONTACTPOINT", "127.0.0.1:26500");
    return ZeebeClient.newClientBuilder()
        .gatewayAddress(addr)
        .usePlaintext()
        .build();
  }
}
