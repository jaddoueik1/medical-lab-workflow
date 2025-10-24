package com.example.labworker.services;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class TwilioSenderService {
  private static final Logger log = LoggerFactory.getLogger(TwilioSenderService.class);

  private final OkHttpClient http = new OkHttpClient();

  private String sid() { return System.getenv().getOrDefault("TWILIO_ACCOUNT_SID", ""); }
  private String token() { return System.getenv().getOrDefault("TWILIO_AUTH_TOKEN", ""); }
  private String from() { return System.getenv().getOrDefault("TWILIO_WHATSAPP_FROM", ""); }

  public boolean sendWhatsApp(String to, String body) {
    try {
      String accountSid = sid();
      String authToken = token();
      String fromNum = from();
      if (accountSid.isBlank() || authToken.isBlank() || fromNum.isBlank()) {
        log.warn("Twilio credentials not set; skipping real send. to={}, body={}", to, body);
        return false;
      }
      HttpUrl url = HttpUrl.parse("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json");
      if (url == null) throw new IllegalArgumentException("Invalid Twilio URL");
      RequestBody form = new FormBody.Builder()
          .add("From", fromNum)
          .add("To", to)
          .add("Body", body)
          .build();
      String basic = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
      Request req = new Request.Builder()
          .url(url)
          .addHeader("Authorization", "Basic " + basic)
          .post(form)
          .build();
      try (Response resp = http.newCall(req).execute()) {
        if (resp.isSuccessful()) {
          log.info("Twilio send OK -> {}", to);
          return true;
        } else {
          String respBody = resp.body() != null ? resp.body().string() : "";
          log.error("Twilio send failed: HTTP {} body={}", resp.code(), respBody);
          return false;
        }
      }
    } catch (Exception e) {
      log.error("Twilio send error: {}", e.toString());
      return false;
    }
  }
}
