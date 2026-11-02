package com.gidajari.repository;

import com.gidajari.entity.OTPVerification;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OTPVerificationRepository extends JpaRepository<OTPVerification, String> {
    Optional<OTPVerification> findTopByPhoneAndUtiliseFalseOrderByCreatedAtDesc(String phone);
    long countByPhoneAndCreatedAtAfter(String phone, Instant depuis);
}
