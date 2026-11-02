package com.gidajari.repository;

import com.gidajari.entity.ONGInstitution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ONGInstitutionRepository extends JpaRepository<ONGInstitution, String> {
}
