package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.RecommendationProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecommendationProfileRepository extends MongoRepository<RecommendationProfile, String> {
    List<RecommendationProfile> findByUserIdOrderByCreatedAtDesc(String userId);
}
