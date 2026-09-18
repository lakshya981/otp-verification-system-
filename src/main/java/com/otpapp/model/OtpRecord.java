package com.otpapp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_records")
public class OtpRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String phoneNumber;

    // We store a hash of the OTP, never the plain code - same principle as
    // storing password hashes instead of plaintext passwords.
    private String otpHash;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    private int attemptCount;
    private boolean verified;

    public OtpRecord() {
        // required by JPA
    }

    public OtpRecord(String phoneNumber, String otpHash, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.phoneNumber = phoneNumber;
        this.otpHash = otpHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.attemptCount = 0;
        this.verified = false;
    }

    public Long getId() {
        return id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getOtpHash() {
        return otpHash;
    }

    public void setOtpHash(String otpHash) {
        this.otpHash = otpHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void incrementAttemptCount() {
        this.attemptCount++;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }
}
