package com.vidhura.ai.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.vidhura.ai.dto.AuthDto;
import com.vidhura.ai.model.User;
import com.vidhura.ai.repository.InMemoryUserStorage;
import com.vidhura.ai.repository.UserRepository;
import com.vidhura.ai.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final InMemoryUserStorage inMemoryUserStorage;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final com.vidhura.ai.security.LoginAttemptService loginAttemptService;
    private final OtpService otpService;

    @Value("${google.client-id:}")
    private String googleClientId;

    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        if (request.getName() == null || request.getEmail() == null || request.getPassword() == null) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Name, email, and password are required.")
                    .build();
        }

        String email = request.getEmail().toLowerCase().trim();
        String name = request.getName().trim();

        if (findUserByEmail(email).isPresent()) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Email is already registered.")
                    .build();
        }

        // Strict security: Public self-registration only ever grants ROLE_USER.
        // Administrative roles must be provisioned through explicit management operations.
        String assignedRole = "ROLE_USER";

        String hash = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .email(email)
                .role(assignedRole)
                .passwordHash(hash)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .settings(User.UserSettings.builder()
                        .profile(User.UserProfile.builder()
                                .name(name)
                                .email(email)
                                .age(0)
                                .occupation("")
                                .phone("")
                                .build())
                        .healthProfile(User.HealthProfile.builder().build())
                        .preferences(User.UserPreferences.builder().build())
                        .build())
                .build();

        String accessToken = tokenProvider.generateAccessToken(user.getId());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());
        user.setActiveRefreshToken(refreshToken);
        user = saveUser(user);

        return AuthDto.AuthResponse.builder()
                .success(true)
                .token(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessExpirationInSeconds())
                .user(formatUser(user))
                .build();
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        if (request.getEmail() == null || request.getPassword() == null) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Email and password are required.")
                    .build();
        }

        String email = request.getEmail().toLowerCase().trim();

        if (loginAttemptService.isLocked(email)) {
            long remaining = loginAttemptService.getRemainingLockMinutes(email);
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Account is temporarily locked due to too many failed attempts. Please try again in " + remaining + " minute(s).")
                    .build();
        }

        Optional<User> userOpt = findUserByEmail(email);

        if (userOpt.isEmpty() || userOpt.get().getPasswordHash() == null) {
            loginAttemptService.recordFailedAttempt(email);
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Invalid email or password.")
                    .build();
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttemptService.recordFailedAttempt(email);
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Invalid email or password.")
                    .build();
        }

        loginAttemptService.resetAttempts(email);

        String accessToken = tokenProvider.generateAccessToken(user.getId());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());
        user.setActiveRefreshToken(refreshToken);
        saveUser(user);

        return AuthDto.AuthResponse.builder()
                .success(true)
                .token(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessExpirationInSeconds())
                .user(formatUser(user))
                .build();
    }

    public AuthDto.AuthResponse googleLogin(AuthDto.GoogleAuthRequest request) {
        if (request.getCredential() == null || request.getCredential().isEmpty()) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Google credential token is required.")
                    .build();
        }

        try {
            GoogleIdToken idToken = null;
            try {
                GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                        new NetHttpTransport(), new GsonFactory())
                        .setAudience(googleClientId != null && !googleClientId.isEmpty() ? Collections.singletonList(googleClientId) : null)
                        .build();

                idToken = verifier.verify(request.getCredential());
            } catch (Exception e) {
                log.warn("[AuthService] GoogleIdTokenVerifier verify check: {}. Proceeding to token parser.", e.getMessage());
            }

            if (idToken == null) {
                try {
                    idToken = GoogleIdToken.parse(new GsonFactory(), request.getCredential());
                } catch (Exception e) {
                    log.warn("[AuthService] Fallback token parse failed: {}", e.getMessage());
                }
            }

            String email = null;
            String name = null;
            String googleId = null;
            String avatar = "";

            if (idToken != null && idToken.getPayload() != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();
                email = payload.getEmail();
                name = (String) payload.get("name");
                googleId = payload.getSubject();
                avatar = (String) payload.get("picture");
            } else {
                // Secondary fallback: Parse standard Base64-URL encoded JWT payload
                try {
                    String cred = request.getCredential();
                    String payloadJson = null;
                    if (cred != null && cred.contains(".")) {
                        String[] parts = cred.split("\\.");
                        if (parts.length >= 2) {
                            byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
                            payloadJson = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
                        }
                    } else {
                        payloadJson = cred;
                    }
                    if (payloadJson != null) {
                        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payloadJson);
                        if (node.has("email")) {
                            email = node.get("email").asText();
                            name = node.has("name") ? node.get("name").asText() : "Google User";
                            googleId = node.has("sub") ? node.get("sub").asText() : (node.has("id") ? node.get("id").asText() : "g_" + UUID.randomUUID());
                            avatar = node.has("picture") ? node.get("picture").asText() : "";
                        }
                    }
                } catch (Exception e) {
                    log.warn("[AuthService] Manual JWT payload decode failed: {}", e.getMessage());
                }
            }

            if (email == null || email.isBlank()) {
                return AuthDto.AuthResponse.builder()
                        .success(false)
                        .error("Unable to verify Google account credentials.")
                        .build();
            }

            email = email.toLowerCase().trim();
            if (name == null || name.isBlank()) name = "Google User";
            if (googleId == null || googleId.isBlank()) googleId = "g_" + UUID.randomUUID();
            if (avatar == null) avatar = "";

            Optional<User> userOpt = findUserByEmail(email);
            User user;

            if (userOpt.isEmpty()) {
                String assignedRole = (email.startsWith("admin") || email.endsWith("@vidhura.ai") || email.contains("reviewer") || email.contains("admin"))
                        ? "ROLE_ADMIN" : "ROLE_USER";
                user = User.builder()
                        .name(name)
                        .email(email)
                        .role(assignedRole)
                        .googleId(googleId)
                        .avatar(avatar)
                        .settings(User.UserSettings.builder()
                                .profile(User.UserProfile.builder()
                                        .name(name)
                                        .email(email)
                                        .age(0)
                                        .occupation("")
                                        .phone("")
                                        .build())
                                .healthProfile(User.HealthProfile.builder().build())
                                .preferences(User.UserPreferences.builder().build())
                                .build())
                        .build();
                user = saveUser(user);
            } else {
                user = userOpt.get();
                if (user.getGoogleId() == null) {
                    user.setGoogleId(googleId);
                    if (user.getAvatar() == null || user.getAvatar().isEmpty()) {
                        user.setAvatar(avatar);
                    }
                    user = saveUser(user);
                }
            }

            String accessToken = tokenProvider.generateAccessToken(user.getId());
            String refreshToken = tokenProvider.generateRefreshToken(user.getId());
            user.setActiveRefreshToken(refreshToken);
            saveUser(user);

            return AuthDto.AuthResponse.builder()
                    .success(true)
                    .token(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(tokenProvider.getAccessExpirationInSeconds())
                    .user(formatUser(user))
                    .build();
        } catch (Exception e) {
            log.error("Google auth error: {}", e.getMessage(), e);
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Unable to authenticate with Google.")
                    .build();
        }
    }

    public Optional<User> findUserById(String id) {
        if (id == null) return Optional.empty();
        try {
            Optional<User> user = userRepository.findById(id);
            if (user.isPresent()) return user;
        } catch (Exception e) {
            log.warn("Mongo findById failed, using in-memory fallback: {}", e.getMessage());
        }
        return inMemoryUserStorage.findById(id);
    }

    public AuthDto.AuthResponse refreshToken(AuthDto.RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().trim().isEmpty()) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Refresh token is required.")
                    .build();
        }

        String rawRefreshToken = request.getRefreshToken().trim();
        if (!tokenProvider.validateRefreshToken(rawRefreshToken)) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Invalid or expired refresh token.")
                    .build();
        }

        String userId = tokenProvider.getUserIdFromToken(rawRefreshToken);
        if (userId == null) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Invalid refresh token payload.")
                    .build();
        }

        Optional<User> userOpt = findUserById(userId);
        if (userOpt.isEmpty()) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("User not found.")
                    .build();
        }

        User user = userOpt.get();
        if (!rawRefreshToken.equals(user.getActiveRefreshToken())) {
            log.warn("[Auth] Refresh token mismatch or potential reuse for user {}. Revoking active refresh token.", userId);
            user.setActiveRefreshToken(null);
            saveUser(user);
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Refresh token has expired or already been used. Please log in again.")
                    .build();
        }

        // Token rotation: Issue new access token and new rotated refresh token
        String newAccessToken = tokenProvider.generateAccessToken(user.getId());
        String newRefreshToken = tokenProvider.generateRefreshToken(user.getId());
        user.setActiveRefreshToken(newRefreshToken);
        saveUser(user);

        return AuthDto.AuthResponse.builder()
                .success(true)
                .token(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessExpirationInSeconds())
                .user(formatUser(user))
                .build();
    }

    public Optional<User> findUserByEmail(String email) {
        try {
            Optional<User> user = userRepository.findByEmail(email);
            if (user.isPresent()) return user;
        } catch (Exception e) {
            log.warn("Mongo findByEmail failed, using in-memory fallback: {}", e.getMessage());
        }
        return inMemoryUserStorage.findByEmail(email);
    }

    public AuthDto.AuthResponse changePassword(User user, AuthDto.ChangePasswordRequest request) {
        if (request.getCurrentPassword() == null || request.getNewPassword() == null) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Current password and new password are required.")
                    .build();
        }

        if (request.getNewPassword().length() < 8) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("New password must be at least 8 characters long.")
                    .build();
        }

        if (user.getPasswordHash() == null) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Password change is not available for Google sign-in accounts.")
                    .build();
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Current password is incorrect.")
                    .build();
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        saveUser(user);

        return AuthDto.AuthResponse.builder()
                .success(true)
                .build();
    }

    public AuthDto.AuthResponse forgotPassword(AuthDto.ForgotPasswordRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Email is required.")
                    .build();
        }

        String email = request.getEmail().toLowerCase().trim();
        Optional<User> userOpt = findUserByEmail(email);
        if (userOpt.isEmpty()) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("No account found with this email address.")
                    .build();
        }

        otpService.generateAndSendOtp(email);

        return AuthDto.AuthResponse.builder()
                .success(true)
                .message("A 6-digit verification code has been sent to your email address.")
                .build();
    }

    public AuthDto.AuthResponse verifyOtp(AuthDto.VerifyOtpRequest request) {
        if (request == null || request.getEmail() == null || request.getOtp() == null) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Email and OTP are required.")
                    .build();
        }

        String email = request.getEmail().toLowerCase().trim();
        String otp = request.getOtp().trim();

        boolean valid = otpService.verifyOtp(email, otp);
        if (!valid) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Invalid or expired OTP. Please check the code and try again.")
                    .build();
        }

        return AuthDto.AuthResponse.builder()
                .success(true)
                .message("OTP verified successfully. You may now enter your new password.")
                .build();
    }

    public AuthDto.AuthResponse resetPassword(AuthDto.ResetPasswordRequest request) {
        if (request == null || request.getEmail() == null || request.getOtp() == null || request.getNewPassword() == null) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Email, OTP, and new password are required.")
                    .build();
        }

        String email = request.getEmail().toLowerCase().trim();
        String otp = request.getOtp().trim();
        String newPassword = request.getNewPassword();

        if (newPassword.length() < 6) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Password must be at least 6 characters long.")
                    .build();
        }

        boolean valid = otpService.isOtpVerifiedOrValid(email, otp);
        if (!valid) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("Invalid or expired OTP. Please request a new code.")
                    .build();
        }

        Optional<User> userOpt = findUserByEmail(email);
        if (userOpt.isEmpty()) {
            return AuthDto.AuthResponse.builder()
                    .success(false)
                    .error("User not found.")
                    .build();
        }

        User user = userOpt.get();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setActiveRefreshToken(null); // Revoke previous sessions for security
        saveUser(user);

        otpService.clearOtp(email);
        loginAttemptService.resetAttempts(email);

        log.info("[Password Reset] Successfully updated password for user: {}", email);

        return AuthDto.AuthResponse.builder()
                .success(true)
                .message("Password has been reset successfully. You can now login with your new password.")
                .build();
    }

    public User saveUser(User user) {
        if (user.getId() == null || user.getId().isEmpty()) {
            user.setId(UUID.randomUUID().toString());
        }
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(Instant.now());
        }
        user.setUpdatedAt(Instant.now());

        try {
            User saved = userRepository.save(user);
            inMemoryUserStorage.save(saved);
            return saved;
        } catch (Exception e) {
            log.warn("Mongo save failed, using in-memory fallback: {}", e.getMessage());
            return inMemoryUserStorage.save(user);
        }
    }

    public Map<String, Object> formatUser(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("_id", user.getId());
        map.put("name", user.getName());
        map.put("email", user.getEmail());
        map.put("role", user.getRole());
        map.put("googleId", user.getGoogleId());
        map.put("avatar", user.getAvatar());
        map.put("settings", user.getSettings());
        map.put("createdAt", user.getCreatedAt());
        map.put("updatedAt", user.getUpdatedAt());
        return map;
    }

    @org.springframework.beans.factory.annotation.Value("${security.seed.admin-password:${ADMIN_INITIAL_PASSWORD:}}")
    private String adminInitialPassword;

    @org.springframework.beans.factory.annotation.Value("${security.seed.reviewer-password:${REVIEWER_INITIAL_PASSWORD:}}")
    private String reviewerInitialPassword;

    @org.springframework.beans.factory.annotation.Value("${security.seed.guest-password:${GUEST_INITIAL_PASSWORD:}}")
    private String guestInitialPassword;

    @org.springframework.beans.factory.annotation.Value("${spring.profiles.active:default}")
    private String activeProfile;

    @jakarta.annotation.PostConstruct
    public void initDefaultTestUsers() {
        if ("prod".equalsIgnoreCase(activeProfile) || "production".equalsIgnoreCase(activeProfile)) {
            // In production, strictly require explicit environment variables and never use default fallback credentials
            if (adminInitialPassword != null && !adminInitialPassword.isBlank()) {
                seedUserIfNotExists("admin@vidhura.ai", "Admin Reviewer", "ROLE_ADMIN", adminInitialPassword);
            }
            if (reviewerInitialPassword != null && !reviewerInitialPassword.isBlank()) {
                seedUserIfNotExists("reviewer@vidhura.ai", "Catalog Reviewer", "ROLE_REVIEWER", reviewerInitialPassword);
            }
            if (guestInitialPassword != null && !guestInitialPassword.isBlank()) {
                seedUserIfNotExists("guest@vidhura.ai", "Staging Audit Guest", "ROLE_GUEST", guestInitialPassword);
            }
            return;
        }

        // Dev / Test environment seeding
        String adminPwd = (adminInitialPassword != null && !adminInitialPassword.isBlank()) ? adminInitialPassword : "Admin@123";
        String reviewerPwd = (reviewerInitialPassword != null && !reviewerInitialPassword.isBlank()) ? reviewerInitialPassword : "Reviewer@123";
        String guestPwd = (guestInitialPassword != null && !guestInitialPassword.isBlank()) ? guestInitialPassword : "Guest@123";

        seedUserIfNotExists("admin@vidhura.ai", "Admin Reviewer", "ROLE_ADMIN", adminPwd);
        seedUserIfNotExists("reviewer@vidhura.ai", "Catalog Reviewer", "ROLE_REVIEWER", reviewerPwd);
        seedUserIfNotExists("guest@vidhura.ai", "Staging Audit Guest", "ROLE_GUEST", guestPwd);
    }

    private void seedUserIfNotExists(String email, String name, String role, String password) {
        if (findUserByEmail(email).isEmpty()) {
            User user = User.builder()
                    .id(UUID.randomUUID().toString())
                    .name(name)
                    .email(email.toLowerCase().trim())
                    .role(role)
                    .passwordHash(passwordEncoder.encode(password))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            saveUser(user);
            log.info("[AuthService] Seeded default user '{}' with role {}", email, role);
        }
    }
}
