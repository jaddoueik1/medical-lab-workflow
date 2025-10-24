package com.example.labworker.workers;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CollectBookingInfoWorker {
  private final ZeebeClient zeebeClient;
  private JobWorker worker;

  public CollectBookingInfoWorker(ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }

  @PostConstruct
  public void start() {
    worker = zeebeClient.newWorker()
        .jobType("collect-booking-info")
        .handler(this::handle)
        .name("collect-booking-info-worker")
        .open();
  }

  private void handle(JobClient client, ActivatedJob job) {
    Map<String, Object> vars = job.getVariablesAsMap();
    if (!vars.containsKey("dob")) {
      vars.put("waitingFor", "dob");
      vars.put("messageToSend", "Please provide your date of birth (YYYY-MM-DD).");
    }
    if (!vars.containsKey("requestedSlot")) {
      vars.put("requestedSlot", vars.getOrDefault("requestedSlotText", "next_available"));
    }
    client.newCompleteCommand(job.getKey()).variables(vars).send().join();
  }

  @PreDestroy
  public void stop() {
    if (worker != null) worker.close();
  }
}
