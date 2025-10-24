package com.example.labworker.workers;

import com.example.labworker.services.InMemoryCalendarService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class FinalizeBookingWorker {
  private static final Logger log = LoggerFactory.getLogger(FinalizeBookingWorker.class);

  private final ZeebeClient zeebeClient;
  private final InMemoryCalendarService calendarService;
  private JobWorker worker;

  public FinalizeBookingWorker(ZeebeClient zeebeClient, InMemoryCalendarService calendarService) {
    this.zeebeClient = zeebeClient;
    this.calendarService = calendarService;
  }

  @PostConstruct
  public void start() {
    worker = zeebeClient.newWorker()
        .jobType("finalize-booking")
        .handler(this::handle)
        .name("finalize-booking-worker")
        .open();
  }

  private void handle(JobClient client, ActivatedJob job) {
    Map<String, Object> vars = job.getVariablesAsMap();
    String selectedSlot = (String) vars.get("selectedSlot");
    String phone = (String) vars.get("phoneNumber");
    String dob = (String) vars.get("dob");

    if (selectedSlot == null) {
      vars.put("messageToSend", "No slot selected by lab team. Please choose a slot.");
      client.newCompleteCommand(job.getKey()).variables(vars).send().join();
      return;
    }

    Map<String, String> appt = new HashMap<>();
    appt.put("phoneNumber", phone);
    appt.put("dob", dob);
    appt.put("notes", String.valueOf(vars.getOrDefault("appointmentNotes", "")));

    boolean ok = calendarService.reserveSlot(selectedSlot, appt);
    if (!ok) {
      vars.put("messageToSend", "Selected slot is no longer available. Please choose another.");
    } else {
      String apptId = UUID.randomUUID().toString();
      vars.put("appointmentId", apptId);
      vars.put("confirmedSlot", selectedSlot);
      vars.put("messageToSend", "Your appointment is confirmed for " + selectedSlot + ". Reference: " + apptId);
    }

    client.newCompleteCommand(job.getKey()).variables(vars).send().join();
  }

  @PreDestroy
  public void stop() {
    if (worker != null) worker.close();
  }
}
