package com.example.labworker.workers;

import com.example.labworker.services.InMemoryCalendarService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CheckAvailabilityWorker {
  private final ZeebeClient zeebeClient;
  private final InMemoryCalendarService calendarService;
  private JobWorker worker;

  public CheckAvailabilityWorker(ZeebeClient zeebeClient, InMemoryCalendarService calendarService) {
    this.zeebeClient = zeebeClient;
    this.calendarService = calendarService;
  }

  @PostConstruct
  public void start() {
    worker = zeebeClient.newWorker()
        .jobType("check-availability")
        .handler(this::handle)
        .name("check-availability-worker")
        .open();
  }

  private void handle(JobClient client, ActivatedJob job) {
    Map<String, Object> vars = job.getVariablesAsMap();
    List<String> slots = calendarService.findNearestAvailableSlots(5);
    vars.put("suggestedSlots", slots);
    vars.put("suggestedSlotsText", String.join(", ", slots));
    client.newCompleteCommand(job.getKey()).variables(vars).send().join();
  }

  @PreDestroy
  public void stop() {
    if (worker != null) worker.close();
  }
}
