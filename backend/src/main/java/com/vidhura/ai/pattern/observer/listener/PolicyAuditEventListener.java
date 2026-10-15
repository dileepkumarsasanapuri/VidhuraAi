package com.vidhura.ai.pattern.observer.listener;

import com.vidhura.ai.model.Policy;
import com.vidhura.ai.pattern.observer.event.PolicyCreatedEvent;
import com.vidhura.ai.pattern.observer.event.PolicyDeletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Concrete Observer (Listener) reacting to domain events emitted during the policy lifecycle.
 * Decouples audit logging and telemetry from core persistence logic.
 */
@Slf4j
@Component
@lombok.RequiredArgsConstructor
public class PolicyAuditEventListener {

    private final com.vidhura.ai.service.AuditService auditService;

    @EventListener
    public void onPolicyCreated(PolicyCreatedEvent event) {
        Policy policy = event.getPolicy();
        log.info("[Observer: AuditEvent] Policy registered: ID='{}', Name='{}', Provider='{}', User='{}', Timestamp='{}'",
                policy.getId(), policy.getName(), policy.getProvider(), event.getUserId(), event.getEventTimestamp());

        auditService.record(event.getUserId(), "POLICY_CREATED", policy.getId(),
                "Registered policy: " + policy.getName() + " (" + policy.getProvider() + ")");

        if (policy.getAnalysis() != null && policy.getAnalysis().getScores() != null) {
            log.info("[Observer: Telemetry] Policy '{}' computed score: Transparency={}, Risk={}, Reliability={}",
                    policy.getName(),
                    policy.getAnalysis().getScores().getTransparency(),
                    policy.getAnalysis().getScores().getRisk(),
                    policy.getAnalysis().getScores().getClaimReliability());
        }
    }

    @EventListener
    public void onPolicyDeleted(PolicyDeletedEvent event) {
        log.info("[Observer: AuditEvent] Policy deleted: ID='{}', User='{}', Timestamp='{}'",
                event.getPolicyId(), event.getUserId(), event.getTimestamp());

        auditService.record(event.getUserId(), "POLICY_DELETED", event.getPolicyId(),
                "Deleted policy ID: " + event.getPolicyId());
    }
}
