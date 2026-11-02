package com.gidajari.repository;

import com.gidajari.entity.CandidatureAO;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CandidatureAORepository extends JpaRepository<CandidatureAO, String> {
    List<CandidatureAO> findByAppelOffreIdOrderByCreatedAtAsc(String appelOffreId);
    List<CandidatureAO> findByCandidatIdOrderByCreatedAtDesc(String candidatId);
    boolean existsByAppelOffreIdAndCandidatId(String appelOffreId, String candidatId);
}
