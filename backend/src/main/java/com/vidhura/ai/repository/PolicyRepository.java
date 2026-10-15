package com.vidhura.ai.repository;

import com.vidhura.ai.model.Policy;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyRepository extends MongoRepository<Policy, String> {
    List<Policy> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<Policy> findByIdAndUserId(String id, String userId);
}
