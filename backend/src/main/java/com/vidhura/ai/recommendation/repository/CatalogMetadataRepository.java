package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.CatalogMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogMetadataRepository extends MongoRepository<CatalogMetadata, String> {
}
