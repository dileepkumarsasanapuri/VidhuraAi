package com.vidhura.ai.recommendation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB document representing a hospital node in the network registry.
 * Captures official ROHINI (Registry of Hospitals in Network of Insurance) identifiers,
 * geographic coordinates, city/pincode locality, and specific insurer cashless agreements.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "hospital_network_nodes")
@CompoundIndexes({
        @CompoundIndex(name = "city_insurer_idx", def = "{'city': 1, 'cashlessAgreements.insurerCode': 1}"),
        @CompoundIndex(name = "pincode_idx", def = "{'pinCode': 1}")
})
public class HospitalNetworkNode {

    @Id
    private String id;

    @Indexed(unique = true)
    private String hospitalId;

    @Indexed
    private String hospitalName;

    /**
     * Standard Registry of Hospitals in Network of Insurance (ROHINI / IIB) Code.
     * Guaranteed unique national healthcare facility identifier in India.
     */
    @Indexed
    private String registryRohiniCode;

    private String address;

    @Indexed
    private String city;

    private String state;

    @Indexed
    private String pinCode;

    private Double latitude;

    private Double longitude;

    /**
     * Category: TERTIARY_CARE, SECONDARY_CARE, DAY_CARE_CENTER, SPECIALTY_EYE_DENTAL, MULTI_SPECIALTY
     */
    private String category;

    @Builder.Default
    private List<CashlessAgreement> cashlessAgreements = new ArrayList<>();

    @Builder.Default
    private Instant verifiedAt = Instant.now();

    @Builder.Default
    private boolean active = true;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashlessAgreement {
        private String insurerCode;
        private String insurerName;
        private boolean cashlessActive;
        /**
         * Tiering: TIER_1_PREFERRED, TIER_2_STANDARD, GIPSA_PPN, GENERAL_NETWORK
         */
        private String tier;
        private LocalDate agreementValidUntil;
        private String sourceDocumentHash;
        private Instant verifiedAt;
        private String verificationStatus;
    }
}
