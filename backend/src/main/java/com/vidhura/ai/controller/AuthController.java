package com.vidhura.ai.controller;

import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.dto.AuthDto;
import com.vidhura.ai.model.User;
import com.vidhura.ai.security.JwtAuthenticationFilter;
import com.vidhura.ai.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthDto.AuthResponse> register(@Valid @RequestBody AuthDto.RegisterRequest request) {
        try {
            AuthDto.AuthResponse response = authService.register(request);
            if (Boolean.TRUE.equals(response.getSuccess())) {
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else if ("Email is already registered.".equals(response.getError())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        } catch (Exception e) {
            log.error("Registration failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AuthDto.AuthResponse.builder().success(false).error("Registration failed: " + e.getMessage()).build());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDto.AuthResponse> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        try {
            AuthDto.AuthResponse response = authService.login(request);
            if (Boolean.TRUE.equals(response.getSuccess())) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
        } catch (Exception e) {
            log.error("Login failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AuthDto.AuthResponse.builder().success(false).error("Login failed: " + e.getMessage()).build());
        }
    }

    @PostMapping("/google")
    public ResponseEntity<AuthDto.AuthResponse> googleLogin(@Valid @RequestBody AuthDto.GoogleAuthRequest request) {
        try {
            AuthDto.AuthResponse response = authService.googleLogin(request);
            if (Boolean.TRUE.equals(response.getSuccess())) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        } catch (Exception e) {
            log.error("Google login failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AuthDto.AuthResponse.builder().success(false).error("Google login failed: " + e.getMessage()).build());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthDto.AuthResponse> refreshToken(@Valid @RequestBody AuthDto.RefreshTokenRequest request) {
        try {
            AuthDto.AuthResponse response = authService.refreshToken(request);
            if (Boolean.TRUE.equals(response.getSuccess())) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AuthDto.AuthResponse.builder().success(false).error("Token refresh failed: " + e.getMessage()).build());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Object>> getCurrentUser(@AuthenticationPrincipal User user) {
        User activeUser = (user != null) ? user : JwtAuthenticationFilter.GUEST_USER;
        return ResponseEntity.ok(ApiResponse.ok(authService.formatUser(activeUser)));
    }

    @PutMapping("/password")
    public ResponseEntity<AuthDto.AuthResponse> changePassword(
            @Valid @RequestBody AuthDto.ChangePasswordRequest request,
            @AuthenticationPrincipal User user) {
        if (user == null || JwtAuthenticationFilter.GUEST_USER.getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthDto.AuthResponse.builder()
                            .success(false)
                            .error("You must be logged in to change your password.")
                            .build());
        }

        AuthDto.AuthResponse response = authService.changePassword(user, request);
        if (Boolean.TRUE.equals(response.getSuccess())) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<AuthDto.AuthResponse> forgotPassword(@Valid @RequestBody AuthDto.ForgotPasswordRequest request) {
        try {
            AuthDto.AuthResponse response = authService.forgotPassword(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Forgot password request failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AuthDto.AuthResponse.builder().success(false).error("Unable to process forgot password request: " + e.getMessage()).build());
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthDto.AuthResponse> verifyOtp(@Valid @RequestBody AuthDto.VerifyOtpRequest request) {
        try {
            AuthDto.AuthResponse response = authService.verifyOtp(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("OTP verification failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AuthDto.AuthResponse.builder().success(false).error("OTP verification failed: " + e.getMessage()).build());
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthDto.AuthResponse> resetPassword(@Valid @RequestBody AuthDto.ResetPasswordRequest request) {
        try {
            AuthDto.AuthResponse response = authService.resetPassword(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Password reset failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AuthDto.AuthResponse.builder().success(false).error("Password reset failed: " + e.getMessage()).build());
        }
    }
}
