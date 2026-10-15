package com.vidhura.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;

    private String googleId;

    private String activeRefreshToken;

    @Builder.Default
    private String avatar = "";

    @Builder.Default
    private UserSettings settings = new UserSettings();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSettings {
        @Builder.Default
        private UserProfile profile = new UserProfile();

        @Builder.Default
        private HealthProfile healthProfile = new HealthProfile();

        @Builder.Default
        private UserPreferences preferences = new UserPreferences();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProfile {
        @Builder.Default
        private String name = "";
        @Builder.Default
        private String email = "";
        @Builder.Default
        private Integer age = 0;
        @Builder.Default
        private String occupation = "";
        @Builder.Default
        private String phone = "";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthProfile {
        @Builder.Default
        private String familyMembers = "";
        @Builder.Default
        private List<String> preExistingConditions = new ArrayList<>();
        @Builder.Default
        private List<String> familyHistory = new ArrayList<>();
        @Builder.Default
        private String bloodGroup = "";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserPreferences {
        @Builder.Default
        private String language = "English";
        @Builder.Default
        private Boolean notifications = true;
        @Builder.Default
        private Boolean darkMode = true;
        @Builder.Default
        private Boolean twoFactorAuth = false;
        @Builder.Default
        private Boolean dataSharing = false;
    }
}
