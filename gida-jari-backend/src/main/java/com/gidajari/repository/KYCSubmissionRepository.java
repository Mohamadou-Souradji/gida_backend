package com.gidajari.repository;

import com.gidajari.entity.KYCSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KYCSubmissionRepository extends JpaRepository<KYCSubmission, String> {
    List<KYCSubmission> findByUserIdOrderByCreatedAtDesc(String userId);
    List<KYCSubmission> findByStatutOrderByCreatedAtAsc(String statut);

    long countByStatut(String statut);
}
