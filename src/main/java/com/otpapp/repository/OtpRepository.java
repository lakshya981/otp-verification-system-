package com.otpapp.repository;

import com.otpapp.model.OtpRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpRepository extends JpaRepository<OtpRecord, Long> {

    // Gets the most recent OTP record for a phone number - we always
    // validate against the latest one requested.
    Optional<OtpRecord> findTopByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);
}
