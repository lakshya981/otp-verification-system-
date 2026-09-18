package com.otpapp.service;

import com.otpapp.model.PendingSms;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Sends the OTP to the user's phone via the Twilio SMS API.
 *
 * Resilience: the actual Twilio call is wrapped with a Resilience4j
 * CircuitBreaker + Retry (config in application.properties, instance name
 * "twilioSms"):
 *   - Retry: if a call fails transiently (e.g. a momentary network blip),
 *     it's retried automatically up to 3 times before giving up.
 *   - CircuitBreaker: if failures keep happening (50%+ of the last 10 calls),
 *     the circuit "opens" and stops calling Twilio for 20 seconds, instead
 *     immediately routing to the fallback below. This avoids hammering an
 *     already-struggling dependency and avoids every request hanging while
 *     waiting on a slow/failing external API - the same pattern services
 *     like Netflix's Hystrix (now Resilience4j) popularized for exactly
 *     this kind of "protect yourself from a flaky downstream dependency"
 *     problem.
 *
 * Fallback: when Twilio can't be reached (circuit open or retries
 * exhausted), the OTP send is queued in memory and retried later by
 * RetryQueueProcessor's scheduled job, instead of being silently dropped.
 *
 * Disabled by default (twilio.enabled=false) so the app can be run and
 * demoed without a Twilio account - in that mode the OTP is just logged
 * to the console instead of being sent, so you can still test the full flow.
 */
@Service
public class SmsService {

    private static final Logger logger = LoggerFactory.getLogger(SmsService.class);

    @Value("${twilio.enabled:false}")
    private boolean twilioEnabled;

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.from-number:}")
    private String fromNumber;

    // In-memory queue of SMS sends that failed and are waiting for retry.
    // RetryQueueProcessor drains this on a schedule. In production this
    // would be a durable queue (e.g. a database table or a message broker)
    // so pending sends survive an app restart - noted in the README.
    private final Queue<PendingSms> retryQueue = new ConcurrentLinkedQueue<>();

    @PostConstruct
    public void init() {
        if (twilioEnabled) {
            Twilio.init(accountSid, authToken);
            logger.info("Twilio SMS sending enabled.");
        } else {
            logger.info("Twilio SMS sending disabled - OTPs will be logged to console instead (set twilio.enabled=true to send real SMS).");
        }
    }

    @CircuitBreaker(name = "twilioSms", fallbackMethod = "sendOtpSmsFallback")
    @Retry(name = "twilioSms")
    public void sendOtpSms(String toPhoneNumber, String otp) {
        if (!twilioEnabled) {
            logger.info("[DEV MODE] OTP for {}: {}", toPhoneNumber, otp);
            return;
        }

        Message.creator(
                new PhoneNumber(toPhoneNumber),
                new PhoneNumber(fromNumber),
                "Your verification code is: " + otp + ". It expires in 5 minutes."
        ).create();
        logger.info("OTP SMS sent to {}", toPhoneNumber);
    }

    /**
     * Called automatically by Resilience4j when sendOtpSms fails all its
     * retries, or when the circuit breaker is open. Must have the same
     * parameters as the original method plus a Throwable.
     */
    private void sendOtpSmsFallback(String toPhoneNumber, String otp, Throwable throwable) {
        logger.warn("Twilio unavailable for {} ({}). Queuing OTP for retry instead of failing the request.",
                toPhoneNumber, throwable.getMessage());
        retryQueue.add(new PendingSms(toPhoneNumber, otp, LocalDateTime.now()));
    }

    public Queue<PendingSms> getRetryQueue() {
        return retryQueue;
    }
}
