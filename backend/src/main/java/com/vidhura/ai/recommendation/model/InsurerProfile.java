package com.vidhura.ai.recommendation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Authoritative Insurer Profile in the Database-Backed Policy Registry.
 * Tracks official identity, IRDAI registration code, category, regulatory metrics,
 * and discovery configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "insurer_profiles")
public class InsurerProfile {

    @Id
    private String id; // e.g. "HDFC_ERGO"

    @Indexed(unique = true)
    private String insurerCode; // e.g. "HDFC_ERGO", "NIC", "OIC"

    private String legalName; // e.g. "HDFC ERGO General Insurance Co. Ltd."

    @Indexed
    private String category; // PUBLIC_GENERAL, PRIVATE_GENERAL, SAHI

    private String irdaiRegistrationNumber; // e.g. "146"

    private String officialWebsite; // e.g. "https://www.hdfcergo.com"

    private String productsListingUrl; // e.g. "https://www.hdfcergo.com/health-insurance"

    @Builder.Default
    private boolean active = true;

    private Instant lastScannedAt;

    // Regulatory performance metrics (from statutory filings)
    private Double incurredClaimsRatio;
    private Double claimSettlementRatio;
    private Double complaintsPer10kPolicies;
    private Double solvencyRatio;

    @Builder.Default
    private List<String> approvedUinPrefixes = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
