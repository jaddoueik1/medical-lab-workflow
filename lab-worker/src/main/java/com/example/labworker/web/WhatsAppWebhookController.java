package com.example.labworker.web;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.camunda.zeebe.client.ZeebeClient;

@RestController
@RequestMapping("/webhook")
public class WhatsAppWebhookController {

    private final ZeebeClient zeebeClient;

    public WhatsAppWebhookController(ZeebeClient zeebeClient) {
        this.zeebeClient = zeebeClient;
    }

    /** Twilio JSON inbound (testing convenience) */
    @PostMapping(value = "/incoming", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> incomingJson(@RequestBody Map<String, Object> body) {
        String from = (String) body.get("from");
        String text = (String) body.get("body");
        return publish(from, text);
    }

    /** Twilio form-encoded webhook: From, Body */
    @PostMapping(value = "/incoming", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> incomingForm(@RequestParam Map<String, String> form) {
        String from = form.getOrDefault("From", form.get("from"));
        String body = form.getOrDefault("Body", form.get("body"));
        return publish(from, body);
    }

    private ResponseEntity<String> publish(String from, String text) {
        if (from == null || text == null) {
            return ResponseEntity.badRequest().body("missing from or body");
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("phoneNumber", from);
        vars.put("messageText", text);

        // Message Start Event: name 'whatsapp_incoming'. Zeebe will start a new
        // instance.
        zeebeClient.newPublishMessageCommand()
                .messageName("whatsapp_incoming")
                .correlationKey(from) // harmless for start events; useful if later we add intermediate catch events
                .timeToLive(Duration.ofSeconds(300000)) // 5 minutes
                .variables(vars)
                .send()
                .join();

        return ResponseEntity.ok("published");
    }
}
