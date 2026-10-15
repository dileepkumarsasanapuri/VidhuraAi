package com.vidhura.ai.controller;

import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.model.User;
import com.vidhura.ai.security.JwtAuthenticationFilter;
import com.vidhura.ai.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final AuthService authService;

    private User getActiveUser(User user) {
        return (user != null) ? user : JwtAuthenticationFilter.GUEST_USER;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<User.UserSettings>> getSettings(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(getActiveUser(user).getSettings()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<User.UserSettings>> updateSettings(
            @RequestBody User.UserSettings updatedSettings,
            @AuthenticationPrincipal User user) {
        User activeUser = getActiveUser(user);
        if (updatedSettings != null) {
            if (updatedSettings.getProfile() != null) {
                activeUser.getSettings().setProfile(updatedSettings.getProfile());
                if (updatedSettings.getProfile().getName() != null && !updatedSettings.getProfile().getName().isBlank()) {
                    activeUser.setName(updatedSettings.getProfile().getName());
                }
            }
            if (updatedSettings.getHealthProfile() != null) activeUser.getSettings().setHealthProfile(updatedSettings.getHealthProfile());
            if (updatedSettings.getPreferences() != null) activeUser.getSettings().setPreferences(updatedSettings.getPreferences());
            authService.saveUser(activeUser);
        }
        return ResponseEntity.ok(ApiResponse.ok(activeUser.getSettings()));
    }
}
