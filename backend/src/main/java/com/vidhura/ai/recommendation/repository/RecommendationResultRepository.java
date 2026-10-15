package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.RecommendationResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecommendationResultRepository extends MongoRepository<RecommendationResult, String> {
    List<RecommendationResult> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<RecommendationResult> findByIdAndUserId(String id, String userId);
}
