package com.vidhura.ai.pattern.observer.event;

import com.vidhura.ai.model.Policy;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Domain event published when a new policy contract is uploaded and saved.
 * Implements the Observer (Publisher-Subscriber) pattern.
 */
@Getter
public class PolicyCreatedEvent extends ApplicationEvent {

    private final Policy policy;
    private final String userId;
    private final Instant eventTimestamp;

    public PolicyCreatedEvent(Object source, Policy policy, String userId) {
        super(source);
        this.policy = policy;
        this.userId = userId;
        this.eventTimestamp = Instant.now();
    }
}
