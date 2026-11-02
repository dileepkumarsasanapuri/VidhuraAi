package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.PremiumQuoteRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PremiumQuoteRepository extends MongoRepository<PremiumQuoteRecord, String> {

    Optional<PremiumQuoteRecord> findByQuoteId(String quoteId);

    Optional<PremiumQuoteRecord> findByQuoteReferenceNumber(String quoteReferenceNumber);

    List<PremiumQuoteRecord> findByPolicyCode(String policyCode);

    List<PremiumQuoteRecord> findByPolicyCodeAndAgeAndSumInsured(String policyCode, int age, BigDecimal sumInsured);

    Optional<PremiumQuoteRecord> findFirstByPolicyCodeAndAgeAndSumInsuredAndPinCodeOrderByQuoteTimestampDesc(
            String policyCode, int age, BigDecimal sumInsured, String pinCode);

    Optional<PremiumQuoteRecord> findFirstByPolicyCodeAndAgeAndSumInsuredOrderByQuoteTimestampDesc(
            String policyCode, int age, BigDecimal sumInsured);

    List<PremiumQuoteRecord> findByVerificationStatus(String verificationStatus);
}
