package com.gidajari.repository;

import com.gidajari.entity.PropositionEtude;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropositionEtudeRepository extends JpaRepository<PropositionEtude, String> {
    List<PropositionEtude> findByExpertId(String expertId);
    List<PropositionEtude> findByExpertIdOrderByCreatedAtDesc(String expertId);
    List<PropositionEtude> findByProjetId(String projetId);
    List<PropositionEtude> findByProjet_Client_IdOrderByCreatedAtDesc(String clientId);
}
