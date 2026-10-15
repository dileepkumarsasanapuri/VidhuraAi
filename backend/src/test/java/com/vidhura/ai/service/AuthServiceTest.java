package com.vidhura.ai.service;

import com.vidhura.ai.dto.AuthDto;
import com.vidhura.ai.model.User;
import com.vidhura.ai.repository.InMemoryUserStorage;
import com.vidhura.ai.repository.UserRepository;
import com.vidhura.ai.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private InMemoryUserStorage inMemoryUserStorage;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    private com.vidhura.ai.security.LoginAttemptService loginAttemptService;
    private OtpService otpService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        loginAttemptService = new com.vidhura.ai.security.LoginAttemptService();
        otpService = new OtpService();
        authService = new AuthService(userRepository, inMemoryUserStorage, passwordEncoder, tokenProvider, loginAttemptService, otpService);
    }

    @Test
    void testRegisterSuccess() {
        AuthDto.RegisterRequest request = new AuthDto.RegisterRequest("Ramesh Kumar", "ramesh@example.com", "Password123!");

        when(userRepository.findByEmail("ramesh@example.com")).thenReturn(Optional.empty());
        when(inMemoryUserStorage.findByEmail("ramesh@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed_password_123");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenProvider.generateAccessToken(anyString())).thenReturn("mock_jwt_token_abc");
        when(tokenProvider.generateRefreshToken(anyString())).thenReturn("mock_refresh_token_xyz");

        AuthDto.AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals("mock_jwt_token_abc", response.getToken());
        assertEquals("mock_refresh_token_xyz", response.getRefreshToken());
        assertNotNull(response.getUser());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testRegisterDuplicateEmail() {
        AuthDto.RegisterRequest request = new AuthDto.RegisterRequest("Ramesh Kumar", "existing@example.com", "Password123!");

        User existingUser = User.builder().id("1").email("existing@example.com").build();
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));

        AuthDto.AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertFalse(response.getSuccess());
        assertEquals("Email is already registered.", response.getError());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testLoginSuccess() {
        AuthDto.LoginRequest request = new AuthDto.LoginRequest("ramesh@example.com", "Password123!");

        User user = User.builder()
                .id("user-123")
                .email("ramesh@example.com")
                .passwordHash("hashed_password_123")
                .name("Ramesh Kumar")
                .build();

        when(userRepository.findByEmail("ramesh@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hashed_password_123")).thenReturn(true);
        when(tokenProvider.generateAccessToken("user-123")).thenReturn("valid_jwt_token");
        when(tokenProvider.generateRefreshToken("user-123")).thenReturn("valid_refresh_token");

        AuthDto.AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals("valid_jwt_token", response.getToken());
        assertEquals("valid_refresh_token", response.getRefreshToken());
    }

    @Test
    void testLoginInvalidPassword() {
        AuthDto.LoginRequest request = new AuthDto.LoginRequest("ramesh@example.com", "WrongPassword");

        User user = User.builder()
                .id("user-123")
                .email("ramesh@example.com")
                .passwordHash("hashed_password_123")
                .build();

        when(userRepository.findByEmail("ramesh@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hashed_password_123")).thenReturn(false);

        AuthDto.AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertFalse(response.getSuccess());
        assertEquals("Invalid email or password.", response.getError());
        assertNull(response.getToken());
    }

    @Test
    void testLoginUserNotFound() {
        AuthDto.LoginRequest request = new AuthDto.LoginRequest("unknown@example.com", "Password123!");

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(inMemoryUserStorage.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        AuthDto.AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertFalse(response.getSuccess());
        assertEquals("Invalid email or password.", response.getError());
    }

    @Test
    void testLoginAccountLockedAfter5FailedAttempts() {
        String email = "victim@example.com";
        AuthDto.LoginRequest badRequest = new AuthDto.LoginRequest(email, "WrongPassword");

        User user = User.builder()
                .id("user-lock")
                .email(email)
                .passwordHash("hashed_pass")
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hashed_pass")).thenReturn(false);

        // Perform 5 failed attempts
        for (int i = 0; i < 5; i++) {
            authService.login(badRequest);
        }

        // 6th attempt should be blocked immediately without checking password
        AuthDto.AuthResponse lockedResponse = authService.login(badRequest);
        assertNotNull(lockedResponse);
        assertFalse(lockedResponse.getSuccess());
        assertTrue(lockedResponse.getError().contains("Account is temporarily locked"));
    }

    @Test
    void testRefreshTokenSuccess() {
        String refreshToken = "valid_refresh_token_abc";
        User user = User.builder()
                .id("user-rot")
                .email("rot@example.com")
                .activeRefreshToken(refreshToken)
                .build();

        when(tokenProvider.validateRefreshToken(refreshToken)).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(refreshToken)).thenReturn("user-rot");
        when(userRepository.findById("user-rot")).thenReturn(Optional.of(user));
        when(tokenProvider.generateAccessToken("user-rot")).thenReturn("new_access_token_123");
        when(tokenProvider.generateRefreshToken("user-rot")).thenReturn("new_rotated_refresh_token_456");

        AuthDto.AuthResponse response = authService.refreshToken(new AuthDto.RefreshTokenRequest(refreshToken));

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals("new_access_token_123", response.getToken());
        assertEquals("new_rotated_refresh_token_456", response.getRefreshToken());
        assertEquals("new_rotated_refresh_token_456", user.getActiveRefreshToken());
    }

    @Test
    void testRefreshTokenStaleOrReused() {
        String staleRefreshToken = "old_stale_token";
        User user = User.builder()
                .id("user-stale")
                .email("stale@example.com")
                .activeRefreshToken("newer_active_token") // Active token differs
                .build();

        when(tokenProvider.validateRefreshToken(staleRefreshToken)).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(staleRefreshToken)).thenReturn("user-stale");
        when(userRepository.findById("user-stale")).thenReturn(Optional.of(user));

        AuthDto.AuthResponse response = authService.refreshToken(new AuthDto.RefreshTokenRequest(staleRefreshToken));

        assertNotNull(response);
        assertFalse(response.getSuccess());
        assertTrue(response.getError().contains("expired or already been used"));
        assertNull(user.getActiveRefreshToken()); // Revoked on reuse attempt
    }

    @Test
    void testForgotPasswordSuccess() {
        User user = User.builder().id("user-fp").email("user@example.com").build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        AuthDto.AuthResponse response = authService.forgotPassword(new AuthDto.ForgotPasswordRequest("user@example.com"));

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals("A 6-digit verification code has been sent to your email address.", response.getMessage());
    }

    @Test
    void testForgotPasswordUserNotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        AuthDto.AuthResponse response = authService.forgotPassword(new AuthDto.ForgotPasswordRequest("unknown@example.com"));

        assertNotNull(response);
        assertFalse(response.getSuccess());
        assertEquals("No account found with this email address.", response.getError());
    }

    @Test
    void testVerifyOtpAndResetPassword() {
        User user = User.builder()
                .id("user-reset")
                .email("reset@example.com")
                .passwordHash("old_hash")
                .build();
        when(userRepository.findByEmail("reset@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewSecret123!")).thenReturn("new_encoded_hash");

        // 1. Generate OTP
        String otp = otpService.generateAndSendOtp("reset@example.com");
        assertNotNull(otp);

        // 2. Verify OTP
        AuthDto.AuthResponse verifyResponse = authService.verifyOtp(new AuthDto.VerifyOtpRequest("reset@example.com", otp));
        assertTrue(verifyResponse.getSuccess());

        // 3. Reset Password
        AuthDto.AuthResponse resetResponse = authService.resetPassword(new AuthDto.ResetPasswordRequest("reset@example.com", otp, "NewSecret123!"));
        assertTrue(resetResponse.getSuccess());
        assertEquals("new_encoded_hash", user.getPasswordHash());
        assertNull(user.getActiveRefreshToken());
    }
}
