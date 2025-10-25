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

    private String sid() {
      return "AC9e470f2ae39d4cdfdf28f7e8658c6d10";
        // return System.getenv("TWILIO_ACCOUNT_SID") != null
                // ? System.getenv("TWILIO_ACCOUNT_SID")
                // : "AC9e470f2ae39d4cdfdf28f7e8658c6d10";
    }

    private String token() {
      return "cc879b23f676d891f9af02385bca83bd";
        // return System.getenv("TWILIO_AUTH_TOKEN") != null
                // ? System.getenv("TWILIO_AUTH_TOKEN")
                // : "cc879b23f676d891f9af02385bca83bd";
    }

    private String from() {
      return "whatsapp:+18316660431";
        // return System.getenv("TWILIO_WHATSAPP_FROM") != null
                // ? System.getenv("TWILIO_WHATSAPP_FROM")
                // : "whatsapp:+18316660431";
    }

    public boolean sendWhatsApp(String to, String body) {
        try {
            String accountSid = sid();
            String authToken = token();
            String fromNum = from();

            if (accountSid.isBlank() || authToken.isBlank() || fromNum.isBlank()) {
                log.warn("Twilio credentials not set; skipping real send. to={}, body={}", to, body);
                return false;
            }

            log.debug("Twilio SID={} token starts with={} from={}", accountSid, authToken.substring(0,4), fromNum);

            HttpUrl url = HttpUrl.parse("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json");
            if (url == null) throw new IllegalArgumentException("Invalid Twilio URL");

            RequestBody form = new FormBody.Builder()
                    .add("From", fromNum)
                    .add("To", to)
                    .add("Body", body)
                    .build();

            String basic = Credentials.basic(accountSid, authToken);
            Request req = new Request.Builder()
                    .url(url)
                    .header("Authorization", basic)
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
            log.error("Twilio send error: {}", e.toString(), e);
            return false;
        }
    }
}
