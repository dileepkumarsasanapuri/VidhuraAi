package com.vidhura.ai.repository;

import com.vidhura.ai.model.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryUserStorage {

    private final Map<String, User> userMap = new ConcurrentHashMap<>();

    public Optional<User> findById(String id) {
        return Optional.ofNullable(userMap.get(id));
    }

    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        String lowerEmail = email.toLowerCase().trim();
        return userMap.values().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(lowerEmail))
                .findFirst();
    }

    public User save(User user) {
        if (user.getId() == null || user.getId().isEmpty()) {
            user.setId(UUID.randomUUID().toString());
            user.setCreatedAt(Instant.now());
        }
        user.setUpdatedAt(Instant.now());
        userMap.put(user.getId(), user);
        return user;
    }

    public List<User> findAll() {
        return new ArrayList<>(userMap.values());
    }
}
