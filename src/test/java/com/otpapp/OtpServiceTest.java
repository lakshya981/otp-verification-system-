package com.otpapp;

import com.otpapp.model.OtpRecord;
import com.otpapp.repository.OtpRepository;
import com.otpapp.service.OtpService;
import com.otpapp.service.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OtpServiceTest {

    private OtpRepository otpRepository;
    private SmsService smsService;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpRepository = mock(OtpRepository.class);
        smsService = mock(SmsService.class);
        otpService = new OtpService(otpRepository, smsService);
    }

    @Test
    void sendOtp_sendsSmsAndSavesRecord_whenNoPreviousOtpExists() {
        when(otpRepository.findTopByPhoneNumberOrderByCreatedAtDesc("+919999999999"))
                .thenReturn(Optional.empty());

        OtpService.SendResult result = otpService.sendOtp("+919999999999");

        assertEquals(OtpService.SendResult.SENT, result);
        verify(otpRepository, times(1)).save(any(OtpRecord.class));
        verify(smsService, times(1)).sendOtpSms(eq("+919999999999"), any(String.class));
    }

    @Test
    void sendOtp_blocksResend_whenCooldownStillActive() {
        OtpRecord recentRecord = new OtpRecord("+919999999999", "hash", LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));
        when(otpRepository.findTopByPhoneNumberOrderByCreatedAtDesc("+919999999999"))
                .thenReturn(Optional.of(recentRecord));

        OtpService.SendResult result = otpService.sendOtp("+919999999999");

        assertEquals(OtpService.SendResult.COOLDOWN_ACTIVE, result);
        verify(smsService, never()).sendOtpSms(any(), any());
    }

    @Test
    void verifyOtp_returnsNotFound_whenNoOtpWasEverRequested() {
        when(otpRepository.findTopByPhoneNumberOrderByCreatedAtDesc("+919999999999"))
                .thenReturn(Optional.empty());

        OtpService.VerifyResult result = otpService.verifyOtp("+919999999999", "123456");

        assertEquals(OtpService.VerifyResult.NOT_FOUND, result);
    }

    @Test
    void verifyOtp_returnsExpired_whenOtpValidityWindowHasPassed() {
        OtpRecord expiredRecord = new OtpRecord("+919999999999", "somehash", LocalDateTime.now().minusMinutes(10), LocalDateTime.now().minusMinutes(5));
        when(otpRepository.findTopByPhoneNumberOrderByCreatedAtDesc("+919999999999"))
                .thenReturn(Optional.of(expiredRecord));

        OtpService.VerifyResult result = otpService.verifyOtp("+919999999999", "123456");

        assertEquals(OtpService.VerifyResult.EXPIRED, result);
    }

    @Test
    void verifyOtp_returnsTooManyAttempts_afterFiveFailedTries() {
        OtpRecord record = new OtpRecord("+919999999999", "correcthash", LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));
        for (int i = 0; i < 5; i++) {
            record.incrementAttemptCount();
        }
        when(otpRepository.findTopByPhoneNumberOrderByCreatedAtDesc("+919999999999"))
                .thenReturn(Optional.of(record));

        OtpService.VerifyResult result = otpService.verifyOtp("+919999999999", "000000");

        assertEquals(OtpService.VerifyResult.TOO_MANY_ATTEMPTS, result);
    }
}
