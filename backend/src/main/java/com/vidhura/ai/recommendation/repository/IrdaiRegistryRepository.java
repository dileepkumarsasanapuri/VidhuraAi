package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.IrdaiRegistryRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IrdaiRegistryRepository extends MongoRepository<IrdaiRegistryRecord, String> {

    Optional<IrdaiRegistryRecord> findByNormalizedUin(String normalizedUin);

    List<IrdaiRegistryRecord> findAllByNormalizedUin(String normalizedUin);

    Optional<IrdaiRegistryRecord> findBySourceUin(String sourceUin);

    Page<IrdaiRegistryRecord> findByNormalizedInsurerCode(String insurerCode, Pageable pageable);

    Page<IrdaiRegistryRecord> findByFinancialYear(String financialYear, Pageable pageable);

    Page<IrdaiRegistryRecord> findByArchived(boolean archived, Pageable pageable);

    Page<IrdaiRegistryRecord> findByClassificationStatus(String classificationStatus, Pageable pageable);

    Page<IrdaiRegistryRecord> findByDuplicateStatus(String duplicateStatus, Pageable pageable);

    @Query("{ $or: [ " +
            "{ 'normalizedUin': { $regex: ?0, $options: 'i' } }, " +
            "{ 'sourceUin': { $regex: ?0, $options: 'i' } }, " +
            "{ 'productName': { $regex: ?0, $options: 'i' } }, " +
            "{ 'insurerName': { $regex: ?0, $options: 'i' } } " +
            "] }")
    Page<IrdaiRegistryRecord> searchRegistry(String keyword, Pageable pageable);

    long countByArchived(boolean archived);

    long countByDuplicateStatus(String duplicateStatus);

    long countByClassificationStatus(String classificationStatus);

    @Query(value = "{ 'normalizedUin': { $in: ['', null] } }", count = true)
    long countMissingUins();

    @Query(value = "{ 'documentDownloadUrl': { $in: ['', null] } }", count = true)
    long countMissingDocumentLinks();
}
