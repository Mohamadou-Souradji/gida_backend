package com.gidajari.repository;

import com.gidajari.entity.CatalogProduit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CatalogProduitRepository extends JpaRepository<CatalogProduit, String> {
    List<CatalogProduit> findByFournisseurId(String fournisseurId);
    List<CatalogProduit> findByActifTrueAndNomContainingIgnoreCaseOrderByPrixUnitaireFcfaAsc(String nom);
}
