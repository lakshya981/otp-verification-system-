package com.otpapp.model;

import java.time.LocalDateTime;

/**
 * Represents an OTP SMS that failed to send (e.g. because Twilio's circuit
 * breaker was open) and is waiting to be retried by the background job in
 * RetryQueueProcessor.
 */
public class PendingSms {

    private final String phoneNumber;
    private final String otp;
    private final LocalDateTime queuedAt;
    private int attempts;

    public PendingSms(String phoneNumber, String otp, LocalDateTime queuedAt) {
        this.phoneNumber = phoneNumber;
        this.otp = otp;
        this.queuedAt = queuedAt;
        this.attempts = 0;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getOtp() {
        return otp;
    }

    public LocalDateTime getQueuedAt() {
        return queuedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void incrementAttempts() {
        this.attempts++;
    }
}
