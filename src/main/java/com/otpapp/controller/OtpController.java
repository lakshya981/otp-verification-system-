package com.otpapp.controller;

import com.otpapp.dto.SendOtpRequest;
import com.otpapp.dto.VerifyOtpRequest;
import com.otpapp.service.OtpService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/otp")
public class OtpController {

    private final OtpService otpService;

    public OtpController(OtpService otpService) {
        this.otpService = otpService;
    }

    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        OtpService.SendResult result = otpService.sendOtp(request.getPhoneNumber());

        return switch (result) {
            case SENT -> ResponseEntity.ok(Map.of("status", "OTP sent successfully"));
            case COOLDOWN_ACTIVE -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("status", "Please wait before requesting another OTP"));
        };
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        OtpService.VerifyResult result = otpService.verifyOtp(request.getPhoneNumber(), request.getOtp());

        return switch (result) {
            case SUCCESS -> ResponseEntity.ok(Map.of("status", "Phone number verified successfully"));
            case EXPIRED -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "OTP has expired. Please request a new one"));
            case INCORRECT -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "Incorrect OTP"));
            case TOO_MANY_ATTEMPTS -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("status", "Too many incorrect attempts. Please request a new OTP"));
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "No OTP request found for this phone number"));
        };
    }
}
