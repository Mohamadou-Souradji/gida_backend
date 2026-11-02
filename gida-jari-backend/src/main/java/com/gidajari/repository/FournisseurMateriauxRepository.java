package com.gidajari.repository;

import com.gidajari.entity.FournisseurMateriaux;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FournisseurMateriauxRepository extends JpaRepository<FournisseurMateriaux, String> {
}
