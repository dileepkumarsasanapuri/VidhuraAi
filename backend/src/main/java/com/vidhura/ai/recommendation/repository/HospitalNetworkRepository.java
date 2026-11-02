package com.vidhura.ai.recommendation.repository;

import com.vidhura.ai.recommendation.model.HospitalNetworkNode;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HospitalNetworkRepository extends MongoRepository<HospitalNetworkNode, String> {

    Optional<HospitalNetworkNode> findByHospitalId(String hospitalId);

    Optional<HospitalNetworkNode> findByRegistryRohiniCode(String registryRohiniCode);

    List<HospitalNetworkNode> findByCityIgnoreCase(String city);

    List<HospitalNetworkNode> findByPinCode(String pinCode);

    List<HospitalNetworkNode> findByHospitalNameContainingIgnoreCase(String name);

    @Query("{ 'cashlessAgreements': { $elemMatch: { 'insurerCode': ?0, 'cashlessActive': true } } }")
    List<HospitalNetworkNode> findByCashlessInsurerCode(String insurerCode);

    @Query("{ 'city': { $regex: ?0, $options: 'i' }, 'cashlessAgreements': { $elemMatch: { 'insurerCode': ?1, 'cashlessActive': true } } }")
    List<HospitalNetworkNode> findByCityAndCashlessInsurer(String city, String insurerCode);

    @Query("{ 'pinCode': ?0, 'cashlessAgreements': { $elemMatch: { 'insurerCode': ?1, 'cashlessActive': true } } }")
    List<HospitalNetworkNode> findByPinCodeAndCashlessInsurer(String pinCode, String insurerCode);

    long countByCityIgnoreCase(String city);

    @Query(value = "{ 'city': { $regex: ?0, $options: 'i' }, 'cashlessAgreements': { $elemMatch: { 'insurerCode': ?1, 'cashlessActive': true } } }", count = true)
    long countByCityAndCashlessInsurer(String city, String insurerCode);
}
