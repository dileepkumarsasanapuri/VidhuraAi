package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.DiscoveredDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscoveredDocumentRepository extends MongoRepository<DiscoveredDocument, String> {
    Optional<DiscoveredDocument> findBySha256Checksum(String sha256Checksum);
    boolean existsBySha256Checksum(String sha256Checksum);
    List<DiscoveredDocument> findBySourceIdOrderByFetchedAtDesc(String sourceId);
    List<DiscoveredDocument> findTop20ByOrderByFetchedAtDesc();
}
