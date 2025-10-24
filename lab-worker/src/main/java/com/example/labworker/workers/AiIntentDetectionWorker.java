package com.example.labworker.workers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class AiIntentDetectionWorker {
  private static final Logger log = LoggerFactory.getLogger(AiIntentDetectionWorker.class);

  private final ZeebeClient zeebeClient;
  private JobWorker worker;
  private final ObjectMapper mapper = new ObjectMapper();

  private final OkHttpClient http;
  private final String apiKey;
  private final String baseUrl;
  private final String model;
  private final int maxRetries;

  public AiIntentDetectionWorker(
      ZeebeClient zeebeClient,
      @Value("${openai.apiBaseUrl}") String baseUrl,
      @Value("${openai.model}") String model,
      @Value("${openai.timeoutSec}") int timeoutSec,
      @Value("${openai.maxRetries}") int maxRetries,
      @Value("${OPENAI_API_KEY:}") String apiKeyEnv
  ) {
    this.zeebeClient = zeebeClient;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.model = model;
    this.maxRetries = Math.max(0, maxRetries);
    this.apiKey = apiKeyEnv;
    this.http = new OkHttpClient.Builder().callTimeout(Duration.ofSeconds(timeoutSec)).build();
  }

  @PostConstruct
  public void start() {
    worker = zeebeClient.newWorker()
        .jobType("ai-intent-detection")
        .handler(this::handle)
        .name("ai-intent-detection-worker")
        .open();
    log.info("Started ai-intent-detection worker with model={}", model);
  }

  private void handle(JobClient client, ActivatedJob job) {
    Map<String, Object> vars = job.getVariablesAsMap();
    String text = String.valueOf(vars.getOrDefault("messageText", ""));
    String phone = String.valueOf(vars.getOrDefault("phoneNumber", ""));

    if (apiKey == null || apiKey.isBlank()) {
      log.error("OPENAI_API_KEY missing; defaulting intent=OTHER");
      vars.put("intent", "OTHER");
      client.newCompleteCommand(job.getKey()).variables(vars).send().join();
      return;
    }

    try {
      ObjectNode req = mapper.createObjectNode();
      req.put("model", model);

      ObjectNode rf = mapper.createObjectNode();
      rf.put("type", "json_object");
      req.set("response_format", rf);

      var msgs = mapper.createArrayNode();
      ObjectNode sys = mapper.createObjectNode();
      sys.put("role", "system");
      String systemPrompt =
          "You are an intent classifier for a medical lab chat assistant in Tyre, Lebanon.\\n" +
          "Extract one of these intents:\\n" +
          "  - BOOK_APPOINTMENT\\n" +
          "  - REQUEST_RESULTS\\n" +
          "  - OTHER\\n" +
          "Also extract optional entities:\\n" +
          "  - dob: ISO date (YYYY-MM-DD) if present\\n" +
          "  - requestedSlotText: any date/time phrase the user mentioned (free text)\\n" +
          "  - handoffToClinician: true iff the user explicitly asks to speak to a clinician/doctor.\\n" +
          "Return STRICT JSON like:\\n" +
          "{\\n" +
          "  \\"intent\\": \\"BOOK_APPOINTMENT\\",\\n" +
          "  \\"entities\\": {\\n" +
          "    \\"dob\\": \\"1990-05-01\\",\\n" +
          "    \\"requestedSlotText\\": \\"tomorrow morning\\",\\n" +
          "    \\"handoffToClinician\\": false\\n" +
          "  }\\n" +
          "}\\n" +
          "If something is missing, omit the key. Never include explanations.";
      sys.put("content", systemPrompt);
      msgs.add(sys);

      ObjectNode user = mapper.createObjectNode();
      user.put("role", "user");
      user.put("content", "User phone: " + phone + "\\nMessage: " + text);
      msgs.add(user);

      req.set("messages", msgs);

      Request httpReq = new Request.Builder()
          .url(baseUrl + "/chat/completions")
          .addHeader("Authorization", "Bearer " + apiKey)
          .addHeader("Content-Type", "application/json")
          .post(RequestBody.create(req.toString().getBytes(StandardCharsets.UTF_8),
              MediaType.parse("application/json")))
          .build();

      String json = executeWithRetry(httpReq, maxRetries);
      if (json == null) {
        vars.put("intent", "OTHER");
      } else {
        JsonNode root = mapper.readTree(json);
        String content = root.path("choices").path(0).path("message").path("content").asText("{}");
        JsonNode out = mapper.readTree(content);
        String intent = out.path("intent").asText("OTHER");
        vars.put("intent", intent);

        JsonNode entities = out.path("entities");
        if (entities.isObject()) {
          if (entities.has("dob")) vars.put("dob", entities.get("dob").asText());
          if (entities.has("requestedSlotText")) vars.put("requestedSlotText", entities.get("requestedSlotText").asText());
          if (entities.has("handoffToClinician")) vars.put("handoffToClinician", entities.get("handoffToClinician").asBoolean(false));
        }
      }

      client.newCompleteCommand(job.getKey()).variables(vars).send().join();

    } catch (Exception ex) {
      log.error("AI intent detection failed: {}", ex.toString());
      vars.put("intent", "OTHER");
      client.newCompleteCommand(job.getKey()).variables(vars).send().join();
    }
  }

  private String executeWithRetry(Request req, int retries) throws Exception {
    int attempt = 0;
    while (true) {
      attempt++;
      try (Response resp = http.newCall(req).execute()) {
        if (resp.isSuccessful() && resp.body() != null) {
          return resp.body().string();
        }
        int code = resp.code();
        if ((code == 429 || (code >= 500 && code < 600)) && attempt <= (retries + 1)) {
          long backoffMs = (long) Math.min(8000, Math.pow(2, attempt) * 250);
          Thread.sleep(backoffMs);
          continue;
        }
        return null;
      } catch (Exception e) {
        if (attempt <= (retries + 1)) {
          long backoffMs = (long) Math.min(8000, Math.pow(2, attempt) * 250);
          Thread.sleep(backoffMs);
          continue;
        }
        throw e;
      }
    }
  }

  @PreDestroy
  public void stop() {
    if (worker != null) worker.close();
  }
}
