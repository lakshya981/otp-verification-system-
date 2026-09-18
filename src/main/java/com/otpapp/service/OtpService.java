package com.otpapp.service;

import com.otpapp.model.OtpRecord;
import com.otpapp.repository.OtpRepository;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class OtpService {

    private static final int OTP_LENGTH = 6;
    private static final Duration OTP_VALIDITY = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private final OtpRepository otpRepository;
    private final SmsService smsService;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpService(OtpRepository otpRepository, SmsService smsService) {
        this.otpRepository = otpRepository;
        this.smsService = smsService;
    }

    public enum SendResult {
        SENT, COOLDOWN_ACTIVE
    }

    public enum VerifyResult {
        SUCCESS, EXPIRED, INCORRECT, TOO_MANY_ATTEMPTS, NOT_FOUND
    }

    /**
     * Generates a new OTP, stores its hash, and sends it via SMS.
     * Enforces a resend cooldown so a user (or attacker) can't spam
     * requests and rack up SMS costs / flood a phone number.
     */
    public SendResult sendOtp(String phoneNumber) {
        Optional<OtpRecord> lastRecord = otpRepository.findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber);

        if (lastRecord.isPresent()) {
            LocalDateTime lastSentAt = lastRecord.get().getCreatedAt();
            if (Duration.between(lastSentAt, LocalDateTime.now()).compareTo(RESEND_COOLDOWN) < 0) {
                return SendResult.COOLDOWN_ACTIVE;
            }
        }

        String otp = generateOtp();
        String otpHash = hash(otp);

        LocalDateTime now = LocalDateTime.now();
        OtpRecord record = new OtpRecord(phoneNumber, otpHash, now, now.plus(OTP_VALIDITY));
        otpRepository.save(record);

        smsService.sendOtpSms(phoneNumber, otp);
        return SendResult.SENT;
    }

    /**
     * Verifies a submitted OTP against the most recent one issued for this
     * phone number. Enforces expiry and a maximum attempt count to make
     * brute-forcing a 6-digit code impractical.
     */
    public VerifyResult verifyOtp(String phoneNumber, String submittedOtp) {
        Optional<OtpRecord> recordOpt = otpRepository.findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber);

        if (recordOpt.isEmpty()) {
            return VerifyResult.NOT_FOUND;
        }

        OtpRecord record = recordOpt.get();

        if (record.isVerified()) {
            return VerifyResult.SUCCESS;
        }

        if (record.getAttemptCount() >= MAX_VERIFY_ATTEMPTS) {
            return VerifyResult.TOO_MANY_ATTEMPTS;
        }

        if (LocalDateTime.now().isAfter(record.getExpiresAt())) {
            return VerifyResult.EXPIRED;
        }

        record.incrementAttemptCount();

        if (record.getOtpHash().equals(hash(submittedOtp))) {
            record.setVerified(true);
            otpRepository.save(record);
            return VerifyResult.SUCCESS;
        }

        otpRepository.save(record);
        return VerifyResult.INCORRECT;
    }

    private String generateOtp() {
        int bound = (int) Math.pow(10, OTP_LENGTH);
        int otp = secureRandom.nextInt(bound);
        return String.format("%0" + OTP_LENGTH + "d", otp);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes());
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
