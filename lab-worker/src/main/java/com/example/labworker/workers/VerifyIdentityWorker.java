package com.example.labworker.workers;

import com.example.labworker.services.ResultsService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class VerifyIdentityWorker {
  private final ZeebeClient zeebeClient;
  private final ResultsService resultsService;
  private JobWorker worker;

  public VerifyIdentityWorker(ZeebeClient zeebeClient, ResultsService resultsService) {
    this.zeebeClient = zeebeClient;
    this.resultsService = resultsService;
  }

  @PostConstruct
  public void start() {
    worker = zeebeClient.newWorker()
        .jobType("verify-identity")
        .handler(this::handle)
        .name("verify-identity-worker")
        .open();
  }

  private void handle(JobClient client, ActivatedJob job) {
    Map<String, Object> vars = job.getVariablesAsMap();
    String phone = (String) vars.get("phoneNumber");
    String dob = (String) vars.get("dob");
    boolean ok = resultsService.verify(phone, dob);
    vars.put("identityVerified", ok);
    if (!ok) {
      vars.put("messageToSend", "Verification failed. Please re-check your DOB (YYYY-MM-DD).");
    }
    client.newCompleteCommand(job.getKey()).variables(vars).send().join();
  }

  @PreDestroy
  public void stop() {
    if (worker != null) worker.close();
  }
}
