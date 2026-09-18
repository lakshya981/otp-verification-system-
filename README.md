# OTP-Based Phone Verification System

A phone number verification system built with **Java, Spring Boot, and the Twilio SMS API**. Generates a one-time password (OTP), sends it via SMS, and verifies it with expiry, hashing, and rate-limiting protections — the same pattern used by real-world login/signup flows.

## Features

- Generates a secure 6-digit OTP using `SecureRandom` (not `Math.random()`, which is predictable)
- **OTPs are never stored in plain text** — only a SHA-256 hash is persisted, same principle as password storage
- **Expiry** — each OTP is valid for 5 minutes
- **Resend cooldown** — 60-second cooldown between OTP requests to the same number, preventing SMS-bombing/cost abuse
- **Attempt limiting** — max 5 verification attempts per OTP, to make brute-forcing a 6-digit code impractical
- Sends the OTP via the **Twilio SMS API** (falls back to logging it to the console if Twilio isn't configured, so the app is fully testable without a Twilio account)
- **Circuit breaker + retry around the Twilio call** (Resilience4j) — if Twilio starts failing, the app stops hammering it, queues the message, and automatically retries later instead of crashing or hanging requests
- Input validation on phone number format (E.164, e.g. `+919876543210`)
- Unit tests with JUnit 5 + Mockito covering the send/verify logic and edge cases

## Resilience: Circuit Breaker + Retry + Retry Queue

This is the most important engineering piece of this project, and the one worth understanding deepest.

**The problem:** `SmsService` calls Twilio's API over the network. Any external API call can fail — Twilio could be down, slow, or rate-limiting you. Without protection, a failing dependency can cascade: every request hangs waiting on Twilio, threads pile up, and the whole app degrades even though the actual bug is in someone else's service, not yours.

**The fix, layered:**
1. **`@Retry`** — if a single call fails (e.g. a one-off network blip), it's automatically retried up to 3 times with a 2-second wait between attempts, before being treated as a real failure.
2. **`@CircuitBreaker`** — if failures keep happening (50%+ of the last 10 calls fail), the circuit "opens": for the next 20 seconds, calls to Twilio are skipped entirely and routed straight to the fallback, instead of every request wasting time waiting on a service that's clearly struggling. After 20 seconds it goes "half-open" and lets a few test calls through to see if Twilio has recovered.
3. **Fallback + retry queue** — when the circuit is open or retries are exhausted, the OTP send doesn't just fail silently. It's added to an in-memory queue (`SmsService.retryQueue`), and a background job (`RetryQueueProcessor`, running every 30 seconds via `@Scheduled`) keeps attempting to send it, up to 5 times, before giving up and logging it as a permanent failure.

**Net effect:** a temporary Twilio outage becomes "the user's OTP arrives a little late" instead of "the request fails and the user is stuck" — a real reliability pattern, not just a demo feature.

### How to see it in action
- Watch the circuit breaker's live state: `http://localhost:8081/actuator/circuitbreakers`
- To actually trigger it opening, set `twilio.enabled=true` with an intentionally wrong Auth Token — every call will fail, and after enough failures you'll see the circuit breaker's state flip from `CLOSED` to `OPEN` in the actuator endpoint and in the logs.

**Production caveat (worth mentioning if asked):** the retry queue here is in-memory, so pending retries are lost if the app restarts. A real production system would back this with a durable queue (a database table or a message broker like RabbitMQ/SQS) instead — this project uses in-memory to keep the demo self-contained, and that trade-off is worth naming explicitly if it comes up in an interview.

## Tech Stack

- **Backend:** Java 17, Spring Boot 3, Spring Data JPA, Spring Validation
- **Database:** H2 (in-memory)
- **External API:** Twilio SDK
- **Testing:** JUnit 5, Mockito
- **Frontend:** Simple HTML/JS test page

## API Endpoints

### Send OTP
```
POST /api/otp/send
Content-Type: application/json

{ "phoneNumber": "+919876543210" }
```

### Verify OTP
```
POST /api/otp/verify
Content-Type: application/json

{ "phoneNumber": "+919876543210", "otp": "123456" }
```

## How to Run

### Prerequisites
- Java 17+, Maven 3.6+

### Steps
```bash
git clone <your-repo-url>
cd otp-verification-system
mvn spring-boot:run
```

Open `http://localhost:8081` in your browser. Enter a phone number and click "Send OTP" — since Twilio is disabled by default, **check your terminal/console logs** to see the OTP that was generated (it's logged there instead of sent as a real SMS). Enter that code to verify.

### Enabling real SMS via Twilio (optional)
1. Sign up for a free trial: https://www.twilio.com/try-twilio
2. Get your Account SID, Auth Token, and a Twilio phone number
3. In `application.properties`, set `twilio.enabled=true` and fill in the three Twilio values
4. Restart the app — OTPs will now be sent as real SMS messages

### Running tests
```bash
mvn test
```

## Possible Extensions
- Add IP-based rate limiting in addition to phone-number-based limiting
- Move from H2 to a persistent database and add a proper `User` entity tied to verification status
- Support OTP delivery via email as a fallback channel
- Add a `/resend` endpoint with its own explicit cooldown messaging (currently `/send` re-used for both cases)

---

