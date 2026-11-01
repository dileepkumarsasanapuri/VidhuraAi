package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.InsurerProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InsurerProfileRepository extends MongoRepository<InsurerProfile, String> {
    Optional<InsurerProfile> findByInsurerCode(String insurerCode);
    List<InsurerProfile> findByActiveTrue();
    boolean existsByInsurerCode(String insurerCode);
}
