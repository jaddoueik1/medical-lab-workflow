# Lab Call-Center Workflow (GPT Intent) — Camunda 8 / Zeebe

Tyre, Lebanon — chat-only. Booking handled via **user task** (lab team). Results via phone+DOB verification, returned as **text** in chat.

## What’s inside
- `docker-compose.yml`: Zeebe, Operate, Tasklist, Elasticsearch, worker app.
- `lab-callcenter-process.bpmn`: process model with **AI intent detection** (OpenAI gpt-5-mini).
- `lab-worker/`: Spring Boot app (Java 17) with Zeebe workers and a simple webhook.

## Quick start
```bash
cd lab-worker
mvn -q -DskipTests package
docker build -t lab-worker:latest .
cd ..
docker compose up --build
```
Open Operate: http://localhost:8081  
Open Tasklist: http://localhost:8082

## Trigger a chat message
```bash
curl -X POST http://localhost:8080/webhook/incoming   -H "Content-Type: application/json"   -d '{"from":"whatsapp:+96170000000","body":"hi, i want to book tomorrow 10am"}'
```

- AI sets `intent=BOOK_APPOINTMENT`, downstream suggests nearest slots.
- A **user task** appears in Tasklist for `lab_team`. Staff sets `selectedSlot` and completes the task.
- `FinalizeBooking` reserves the slot (max 3 per 30-min), then `send-whatsapp` confirms to the customer (logs/stub).

Results request:
```bash
curl -X POST http://localhost:8080/webhook/incoming   -H "Content-Type: application/json"   -d '{"from":"whatsapp:+96170000000","body":"please send my results, my DOB is 1988-01-01"}'
```

## Env vars
- `OPENAI_API_KEY` (required), `OPENAI_MODEL` (default `gpt-5-mini`)
- `ZEEBE_CLIENT_BROKER_CONTACTPOINT` (default `127.0.0.1:26500` in app; compose sets `zeebe:26500`)
- Twilio vars are present but the demo **logs** messages instead of sending.

## Notes
- Demo only (in-memory). Replace services with real APIs, add security, and avoid logging PHI in production.


## Twilio WhatsApp setup (optional but supported now)
1. In the Twilio Console, enable WhatsApp sandbox or your approved sender.
2. Set env vars in `docker-compose.yml` under `lab-worker`:
   - `TWILIO_ACCOUNT_SID`
   - `TWILIO_AUTH_TOKEN`
   - `TWILIO_WHATSAPP_FROM` (e.g., `whatsapp:+1415XXXXXXX`)
3. Expose the webhook with a tunnel (e.g., `ngrok http 8080`) and set Twilio WhatsApp **Webhook URL** to:
   `https://<your-tunnel>/webhook/incoming` (HTTP POST, **application/x-www-form-urlencoded**)
4. The process uses a **Message Start Event** named `whatsapp_incoming`. Incoming messages publish a Zeebe message
   with `correlationKey = From`. Zeebe starts a new process instance per conversation.

### Test with curl (form-encoded like Twilio)
```bash
curl -X POST http://localhost:8080/webhook/incoming   -H "Content-Type: application/x-www-form-urlencoded"   --data-urlencode 'From=whatsapp:+96170000000'   --data-urlencode 'Body=book me tomorrow 10am'
```
