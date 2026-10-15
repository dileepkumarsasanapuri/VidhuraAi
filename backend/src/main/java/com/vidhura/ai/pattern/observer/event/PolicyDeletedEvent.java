package com.vidhura.ai.pattern.observer.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Domain event published when a policy is removed.
 * Implements the Observer (Publisher-Subscriber) pattern.
 */
@Getter
public class PolicyDeletedEvent extends ApplicationEvent {

    private final String policyId;
    private final String userId;
    private final Instant eventTimestamp;

    public PolicyDeletedEvent(Object source, String policyId, String userId) {
        super(source);
        this.policyId = policyId;
        this.userId = userId;
        this.eventTimestamp = Instant.now();
    }
}
