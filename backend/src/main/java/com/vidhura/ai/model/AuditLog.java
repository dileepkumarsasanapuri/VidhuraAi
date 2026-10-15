package com.vidhura.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "audit_logs")
public class AuditLog {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String action; // "POLICY_CREATED", "POLICY_DELETED", "LOGIN_SUCCESS", "LOGIN_FAILED", "TOKEN_REFRESH"

    private String entityId;

    private String details;

    @CreatedDate
    private Instant timestamp;
}
