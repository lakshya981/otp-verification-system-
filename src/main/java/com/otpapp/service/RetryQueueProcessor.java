package com.otpapp.service;

import com.otpapp.model.PendingSms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Queue;

/**
 * Runs every 30 seconds and retries any SMS sends that failed earlier
 * (because Twilio's circuit breaker was open or the call kept failing).
 *
 * This turns a transient Twilio outage into "the user's SMS arrives a bit
 * late" instead of "the SMS is silently lost" - a meaningfully better
 * failure mode for a verification flow.
 *
 * Max 5 attempts per message, after which it's dropped and logged as
 * permanently failed (in production you'd alert on this rather than just log it).
 */
@Component
public class RetryQueueProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RetryQueueProcessor.class);
    private static final int MAX_RETRY_ATTEMPTS = 5;

    private final SmsService smsService;

    public RetryQueueProcessor(SmsService smsService) {
        this.smsService = smsService;
    }

    @Scheduled(fixedDelay = 30000)
    public void processRetryQueue() {
        Queue<PendingSms> queue = smsService.getRetryQueue();
        int size = queue.size();

        if (size == 0) {
            return;
        }

        logger.info("Processing {} pending SMS retries...", size);

        for (int i = 0; i < size; i++) {
            PendingSms pending = queue.poll();
            if (pending == null) {
                continue;
            }

            pending.incrementAttempts();

            if (pending.getAttempts() > MAX_RETRY_ATTEMPTS) {
                logger.error("OTP SMS to {} permanently failed after {} attempts - dropping.",
                        pending.getPhoneNumber(), pending.getAttempts() - 1);
                continue;
            }

            try {
                smsService.sendOtpSms(pending.getPhoneNumber(), pending.getOtp());
                logger.info("Retry succeeded for {}", pending.getPhoneNumber());
            } catch (Exception e) {
                logger.warn("Retry attempt {} failed for {}, re-queuing.", pending.getAttempts(), pending.getPhoneNumber());
                queue.add(pending);
            }
        }
    }
}
