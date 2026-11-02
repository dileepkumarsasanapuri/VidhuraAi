package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.StructuredPolicy;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StructuredPolicyRepository extends MongoRepository<StructuredPolicy, String> {
    @Query("{ '$or': [ { 'policyCode': ?0 }, { 'policyAliases': ?0 } ] }")
    Optional<StructuredPolicy> findByPolicyCode(String policyCode);

    List<StructuredPolicy> findByActiveTrue();
    List<StructuredPolicy> findByActiveTrueAndVerificationStatusIn(List<String> verificationStatuses);
    List<StructuredPolicy> findByVerificationStatus(String verificationStatus);

    @Query("{ '$or': [ { 'irdaUin': ?0 }, { 'historicalUins': ?0 } ] }")
    Optional<StructuredPolicy> findByIrdaUin(String irdaUin);

    long countByActiveTrue();

    @Query(value = "{ '$or': [ { 'policyCode': ?0 }, { 'policyAliases': ?0 } ] }", exists = true)
    boolean existsByPolicyCode(String policyCode);

    @Query(value = "{ '$or': [ { 'irdaUin': ?0 }, { 'historicalUins': ?0 } ] }", exists = true)
    boolean existsByIrdaUin(String irdaUin);
}
