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
public class FetchResultsWorker {
  private final ZeebeClient zeebeClient;
  private final ResultsService resultsService;
  private JobWorker worker;

  public FetchResultsWorker(ZeebeClient zeebeClient, ResultsService resultsService) {
    this.zeebeClient = zeebeClient;
    this.resultsService = resultsService;
  }

  @PostConstruct
  public void start() {
    worker = zeebeClient.newWorker()
        .jobType("fetch-results")
        .handler(this::handle)
        .name("fetch-results-worker")
        .open();
  }

  private void handle(JobClient client, ActivatedJob job) {
    Map<String, Object> vars = job.getVariablesAsMap();
    String phone = (String) vars.get("phoneNumber");
    Boolean verified = (Boolean) vars.getOrDefault("identityVerified", false);
    if (Boolean.TRUE.equals(verified)) {
      vars.put("resultText", resultsService.fetchResultsText(phone));
    } else {
      vars.put("resultText", "Cannot share results without successful verification.");
    }
    client.newCompleteCommand(job.getKey()).variables(vars).send().join();
  }

  @PreDestroy
  public void stop() {
    if (worker != null) worker.close();
  }
}
