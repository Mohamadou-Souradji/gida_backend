package com.gidajari.repository;

import com.gidajari.entity.PhaseEtude;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PhaseEtudeRepository extends JpaRepository<PhaseEtude, String> {
}
