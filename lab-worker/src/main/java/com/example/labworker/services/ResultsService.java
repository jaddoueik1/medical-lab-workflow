package com.example.labworker.services;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ResultsService {
  private final Map<String, Map<String,String>> store = new HashMap<>();

  public ResultsService() {
    Map<String,String> data = new HashMap<>();
    data.put("dob", "1988-01-01");
    data.put("result", "CBC: Normal. Glucose: 95 mg/dL. Lipids: within normal range.");
    store.put("whatsapp:+96170000000", data);
  }

  public boolean verify(String phone, String dob) {
    if (phone == null || dob == null) return false;
    Map<String,String> entry = store.get(phone);
    return entry != null && dob.equals(entry.get("dob"));
  }

  public String fetchResultsText(String phone) {
    Map<String,String> entry = store.get(phone);
    return (entry == null) ? "No results found for the provided details." : entry.get("result");
  }
}
