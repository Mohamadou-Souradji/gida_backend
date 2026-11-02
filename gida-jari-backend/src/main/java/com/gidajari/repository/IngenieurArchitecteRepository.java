package com.gidajari.repository;

import com.gidajari.entity.IngenieurArchitecte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngenieurArchitecteRepository extends JpaRepository<IngenieurArchitecte, String> {
}
