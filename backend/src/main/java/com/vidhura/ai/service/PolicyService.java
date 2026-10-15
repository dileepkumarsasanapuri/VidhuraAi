package com.vidhura.ai.service;

import com.vidhura.ai.model.Policy;
import com.vidhura.ai.pattern.observer.event.PolicyCreatedEvent;
import com.vidhura.ai.pattern.observer.event.PolicyDeletedEvent;
import com.vidhura.ai.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final ApplicationEventPublisher eventPublisher;

    // In-memory fallback store keyed by policy ID
    private final Map<String, Policy> inMemoryStore = new ConcurrentHashMap<>();

    @Cacheable(value = "userPolicies", key = "#userId")
    public List<Policy> getUserPolicies(String userId) {
        try {
            List<Policy> mongoPolicies = policyRepository.findByUserIdOrderByCreatedAtDesc(userId);
            if (!mongoPolicies.isEmpty()) {
                return mongoPolicies;
            }
        } catch (Exception e) {
            log.warn("MongoDB findByUserId failed ({}), using in-memory store.", e.getMessage());
        }
        // Return in-memory policies for this userId
        return inMemoryStore.values().stream()
                .filter(p -> userId.equals(p.getUserId()))
                .sorted(Comparator.comparing(
                        (Policy p) -> p.getCreatedAt() != null ? p.getCreatedAt() : Instant.EPOCH
                ).reversed())
                .collect(Collectors.toList());
    }

    public Optional<Policy> getPolicyById(String id, String userId) {
        try {
            Optional<Policy> mongoResult = policyRepository.findByIdAndUserId(id, userId);
            if (mongoResult.isPresent()) {
                return mongoResult;
            }
        } catch (Exception e) {
            log.warn("MongoDB findByIdAndUserId failed ({}), using in-memory store.", e.getMessage());
        }
        // Fallback: check in-memory store
        Policy memPolicy = inMemoryStore.get(id);
        if (memPolicy != null && userId.equals(memPolicy.getUserId())) {
            return Optional.of(memPolicy);
        }
        return Optional.empty();
    }

    public Optional<Policy> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            Optional<Policy> mongoResult = policyRepository.findById(id);
            if (mongoResult.isPresent()) {
                return mongoResult;
            }
        } catch (Exception e) {
            log.warn("MongoDB findById failed ({}), checking in-memory store.", e.getMessage());
        }
        return Optional.ofNullable(inMemoryStore.get(id));
    }

    @CacheEvict(value = {"userPolicies", "analytics"}, key = "#userId")
    public Policy createPolicy(String userId, Policy policyData) {
        policyData.setUserId(userId);
        if (policyData.getCreatedAt() == null) {
            policyData.setCreatedAt(Instant.now());
        }
        policyData.setUpdatedAt(Instant.now());

        Policy result;
        try {
            Policy saved = policyRepository.save(policyData);
            // Also add to in-memory store
            inMemoryStore.put(saved.getId(), saved);
            result = saved;
        } catch (Exception e) {
            log.warn("MongoDB save failed ({}), storing in-memory.", e.getMessage());
            // Generate an ID if MongoDB didn't assign one
            if (policyData.getId() == null || policyData.getId().isEmpty()) {
                policyData.setId(UUID.randomUUID().toString());
            }
            inMemoryStore.put(policyData.getId(), policyData);
            result = policyData;
        }

        // Observer Pattern: notify all registered event listeners
        try {
            eventPublisher.publishEvent(new PolicyCreatedEvent(this, result, userId));
        } catch (Exception evEx) {
            log.warn("Event publication for PolicyCreatedEvent failed: {}", evEx.getMessage());
        }

        return result;
    }

    @CacheEvict(value = {"userPolicies", "analytics"}, key = "#userId")
    public boolean deletePolicy(String id, String userId) {
        boolean deleted = false;
        try {
            Optional<Policy> policy = policyRepository.findByIdAndUserId(id, userId);
            if (policy.isPresent()) {
                policyRepository.delete(policy.get());
                deleted = true;
            }
        } catch (Exception e) {
            log.warn("MongoDB delete failed ({}), removing from in-memory store.", e.getMessage());
        }
        // Also remove from in-memory store
        Policy removed = inMemoryStore.remove(id);
        boolean wasRemoved = deleted || removed != null;

        if (wasRemoved) {
            // Observer Pattern: notify all registered event listeners
            try {
                eventPublisher.publishEvent(new PolicyDeletedEvent(this, id, userId));
            } catch (Exception evEx) {
                log.warn("Event publication for PolicyDeletedEvent failed: {}", evEx.getMessage());
            }
        }

        return wasRemoved;
    }
}
