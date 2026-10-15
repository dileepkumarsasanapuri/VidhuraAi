package com.vidhura.ai.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, AttemptInfo> attemptsCache = new ConcurrentHashMap<>();

    public static class AttemptInfo {
        private int attempts;
        private Instant lockedUntil;

        public AttemptInfo(int attempts) {
            this.attempts = attempts;
        }

        public int getAttempts() {
            return attempts;
        }

        public void increment() {
            this.attempts++;
        }

        public Instant getLockedUntil() {
            return lockedUntil;
        }

        public void setLockedUntil(Instant lockedUntil) {
            this.lockedUntil = lockedUntil;
        }
    }

    public boolean isLocked(String email) {
        if (email == null) {
            return false;
        }
        String key = email.toLowerCase().trim();
        AttemptInfo info = attemptsCache.get(key);
        if (info == null) {
            return false;
        }

        if (info.getLockedUntil() != null) {
            if (Instant.now().isBefore(info.getLockedUntil())) {
                return true;
            } else {
                // Lock has expired
                attemptsCache.remove(key);
                return false;
            }
        }

        return false;
    }

    public long getRemainingLockMinutes(String email) {
        if (email == null) return 0;
        AttemptInfo info = attemptsCache.get(email.toLowerCase().trim());
        if (info == null || info.getLockedUntil() == null) return 0;
        long seconds = Duration.between(Instant.now(), info.getLockedUntil()).getSeconds();
        return Math.max(1, (seconds + 59) / 60);
    }

    public void recordFailedAttempt(String email) {
        if (email == null) return;
        String key = email.toLowerCase().trim();
        AttemptInfo info = attemptsCache.computeIfAbsent(key, k -> new AttemptInfo(0));
        info.increment();

        if (info.getAttempts() >= MAX_FAILED_ATTEMPTS && info.getLockedUntil() == null) {
            info.setLockedUntil(Instant.now().plus(LOCK_DURATION));
            log.warn("[BruteForce Protection] Account for email '{}' has been locked for 15 minutes after {} failed attempts.",
                    key, info.getAttempts());
        }
    }

    public void resetAttempts(String email) {
        if (email == null) return;
        attemptsCache.remove(email.toLowerCase().trim());
    }
}
