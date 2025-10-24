package com.example.labworker.workers;

import com.example.labworker.services.TwilioSenderService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SendWhatsAppWorker {
  private static final Logger log = LoggerFactory.getLogger(SendWhatsAppWorker.class);
  private final ZeebeClient zeebeClient;
  private final TwilioSenderService twilio;
  private JobWorker worker;

  public SendWhatsAppWorker(ZeebeClient zeebeClient, TwilioSenderService twilio) {
    this.zeebeClient = zeebeClient;
    this.twilio = twilio;
  }

  @PostConstruct
  public void start() {
    worker = zeebeClient.newWorker()
        .jobType("send-whatsapp")
        .handler(this::handle)
        .name("send-whatsapp-worker")
        .open();
  }

  private void handle(JobClient client, ActivatedJob job) {
    Map<String, Object> vars = job.getVariablesAsMap();
    String to = String.valueOf(vars.getOrDefault("phoneNumber", "unknown"));
    String message;
    if (vars.containsKey("resultText")) {
      message = String.valueOf(vars.get("resultText"));
    } else if (vars.containsKey("messageToSend")) {
      message = String.valueOf(vars.get("messageToSend"));
    } else if (vars.containsKey("confirmedSlot")) {
      message = "Your appointment is confirmed for " + vars.get("confirmedSlot") + ".";
    } else {
      message = "Thank you for contacting Tyre Medical Lab. How can we help you today?";
    }

    boolean sent = twilio.sendWhatsApp(to, message);
    if (!sent) {
      // fallback to log only
      log.info("[WHATSAPP LOG -> {}] {}", to, message);
    }

    client.newCompleteCommand(job.getKey()).send().join();
  }

  @PreDestroy
  public void stop() {
    if (worker != null) worker.close();
  }
}
