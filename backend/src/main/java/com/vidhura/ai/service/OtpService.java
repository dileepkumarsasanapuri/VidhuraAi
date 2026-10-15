package com.vidhura.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing 6-digit One-Time Passwords (OTP) for account verification and password resets.
 */
@Slf4j
@Service
public class OtpService {

    private static final Duration OTP_VALIDITY = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 5;
    private final SecureRandom secureRandom = new SecureRandom();
    private final EmailService emailService;

    public OtpService() {
        this.emailService = new EmailService();
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OtpService(EmailService emailService) {
        this.emailService = emailService;
    }

    public static class OtpEntry {
        private final String otp;
        private final Instant expiresAt;
        private int attempts;
        private boolean verified;

        public OtpEntry(String otp, Instant expiresAt) {
            this.otp = otp;
            this.expiresAt = expiresAt;
            this.attempts = 0;
            this.verified = false;
        }

        public String getOtp() {
            return otp;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }

        public int getAttempts() {
            return attempts;
        }

        public void incrementAttempts() {
            this.attempts++;
        }

        public boolean isVerified() {
            return verified;
        }

        public void setVerified(boolean verified) {
            this.verified = verified;
        }
    }

    private final Map<String, OtpEntry> otpStorage = new ConcurrentHashMap<>();

    /**
     * Generates a cryptographically random 6-digit OTP and logs/dispatches the simulated email.
     */
    public String generateAndSendOtp(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email cannot be null");
        }
        String normalizedEmail = email.toLowerCase().trim();

        // Generate 6-digit cryptographic OTP between 100000 and 999999
        int code = 100_000 + secureRandom.nextInt(900_000);
        String otp = String.valueOf(code);

        Instant expiresAt = Instant.now().plus(OTP_VALIDITY);
        otpStorage.put(normalizedEmail, new OtpEntry(otp, expiresAt));

        // Dispatch transactional email via SMTP with simulated fallback
        emailService.sendPasswordResetOtpEmail(normalizedEmail, otp);

        return otp;
    }

    /**
     * Validates whether the supplied OTP matches and is still within the 10-minute validity window.
     */
    public boolean verifyOtp(String email, String inputOtp) {
        if (email == null || inputOtp == null) {
            return false;
        }
        String normalizedEmail = email.toLowerCase().trim();
        OtpEntry entry = otpStorage.get(normalizedEmail);

        if (entry == null) {
            log.warn("[Password Reset OTP] No OTP found for: {}", normalizedEmail);
            return false;
        }

        if (Instant.now().isAfter(entry.getExpiresAt())) {
            log.warn("[Password Reset OTP] OTP expired for: {}", normalizedEmail);
            otpStorage.remove(normalizedEmail);
            return false;
        }

        entry.incrementAttempts();
        if (entry.getAttempts() > MAX_ATTEMPTS) {
            log.warn("[Password Reset OTP] Too many failed attempts for: {}. Invalidating OTP.", normalizedEmail);
            otpStorage.remove(normalizedEmail);
            return false;
        }

        if (entry.getOtp().equals(inputOtp.trim())) {
            entry.setVerified(true);
            log.info("[Password Reset OTP] OTP verified successfully for: {}", normalizedEmail);
            return true;
        }

        log.warn("[Password Reset OTP] Incorrect OTP entered for: {}. Attempt {} of {}", normalizedEmail, entry.getAttempts(), MAX_ATTEMPTS);
        return false;
    }

    /**
     * Checks if the OTP for this email was previously verified or matches directly.
     */
    public boolean isOtpVerifiedOrValid(String email, String inputOtp) {
        if (email == null) return false;
        String normalizedEmail = email.toLowerCase().trim();
        OtpEntry entry = otpStorage.get(normalizedEmail);
        if (entry == null) return false;
        if (Instant.now().isAfter(entry.getExpiresAt())) {
            otpStorage.remove(normalizedEmail);
            return false;
        }
        if (entry.isVerified()) {
            return true;
        }
        return verifyOtp(normalizedEmail, inputOtp);
    }

    /**
     * Clears any active OTP for the specified email once the reset is completed.
     */
    public void clearOtp(String email) {
        if (email != null) {
            otpStorage.remove(email.toLowerCase().trim());
        }
    }

    /**
     * Helper for test fixtures.
     */
    public void setOtpForTesting(String email, String otp, Instant expiresAt) {
        otpStorage.put(email.toLowerCase().trim(), new OtpEntry(otp, expiresAt));
    }
}
