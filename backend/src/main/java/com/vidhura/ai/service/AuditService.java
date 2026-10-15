package com.vidhura.ai.service;

import com.vidhura.ai.model.AuditLog;
import com.vidhura.ai.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final List<AuditLog> inMemoryAuditLogs = new CopyOnWriteArrayList<>();

    public void record(String userId, String action, String entityId, String details) {
        AuditLog auditLog = AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .action(action)
                .entityId(entityId)
                .details(details)
                .timestamp(Instant.now())
                .build();

        inMemoryAuditLogs.add(0, auditLog);
        if (inMemoryAuditLogs.size() > 500) {
            inMemoryAuditLogs.remove(inMemoryAuditLogs.size() - 1);
        }

        try {
            auditLogRepository.save(auditLog);
            log.info("[AuditService] Successfully persisted audit event: {} for user {}", action, userId);
        } catch (Exception e) {
            log.warn("[AuditService] MongoDB audit save failed, using in-memory fallback: {}", e.getMessage());
        }
    }

    public List<AuditLog> getLogsForUser(String userId) {
        try {
            List<AuditLog> logs = auditLogRepository.findByUserIdOrderByTimestampDesc(userId);
            if (logs != null && !logs.isEmpty()) return logs;
        } catch (Exception e) {
            log.warn("[AuditService] MongoDB audit fetch failed, using in-memory: {}", e.getMessage());
        }

        return inMemoryAuditLogs.stream()
                .filter(l -> userId == null || userId.equals(l.getUserId()))
                .limit(50)
                .collect(Collectors.toList());
    }

    public List<AuditLog> getRecentLogs() {
        try {
            List<AuditLog> logs = auditLogRepository.findTop50ByOrderByTimestampDesc();
            if (logs != null && !logs.isEmpty()) return logs;
        } catch (Exception e) {
            log.warn("[AuditService] MongoDB top audits fetch failed, using in-memory: {}", e.getMessage());
        }
        return new ArrayList<>(inMemoryAuditLogs.stream().limit(50).collect(Collectors.toList()));
    }
}
