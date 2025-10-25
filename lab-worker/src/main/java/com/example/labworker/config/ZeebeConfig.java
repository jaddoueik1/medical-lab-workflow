package com.example.labworker.config;

import io.camunda.zeebe.client.ZeebeClient;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ZeebeConfig {

  @Bean
  public ZeebeClient zeebeClient() {
    String addr = System.getenv().getOrDefault("ZEEBE_CLIENT_BROKER_CONTACTPOINT", "localhost:26500");
    log.info("Connecting to Zeebe broker at {}", addr);
    return ZeebeClient.newClientBuilder()
        .gatewayAddress(addr)
        .usePlaintext()
        .build();
  }
}
