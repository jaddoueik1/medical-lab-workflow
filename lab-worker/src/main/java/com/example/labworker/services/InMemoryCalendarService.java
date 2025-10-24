package com.example.labworker.services;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryCalendarService {

  private final Map<String, List<Map<String, String>>> calendar = new ConcurrentHashMap<>();
  private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
  private static final int MAX_PER_SLOT = 3;

  public List<String> findNearestAvailableSlots(int count) {
    ZoneId zone = ZoneId.of("Asia/Beirut");
    LocalDateTime now = LocalDateTime.now(zone).plusMinutes(30);
    List<String> out = new ArrayList<>();
    LocalDateTime cursor = now.withMinute((now.getMinute() < 30) ? 30 : 0);
    if (now.getMinute() >= 30) cursor = cursor.plusHours(1);
    while (out.size() < count) {
      int hour = cursor.getHour();
      if (hour >= 8 && hour < 16) {
        String key = cursor.format(fmt);
        int current = calendar.getOrDefault(key, Collections.emptyList()).size();
        if (current < MAX_PER_SLOT) out.add(key);
      }
      cursor = cursor.plusMinutes(30);
    }
    return out;
  }

  public boolean reserveSlot(String slotKey, Map<String, String> appointment) {
    calendar.putIfAbsent(slotKey, Collections.synchronizedList(new ArrayList<>()));
    List<Map<String,String>> list = calendar.get(slotKey);
    synchronized (list) {
      if (list.size() >= MAX_PER_SLOT) return false;
      list.add(appointment);
      return true;
    }
  }
}
