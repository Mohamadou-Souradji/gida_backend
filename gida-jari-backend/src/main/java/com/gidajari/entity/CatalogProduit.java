package com.gidajari.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Entite P2 - Article du catalogue d'un fournisseur de materiaux.
 * Composition: CatalogProduit *-- FournisseurMateriaux.
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "catalog_produit")
@Getter
@Setter
@NoArgsConstructor
public class CatalogProduit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fournisseur_id", nullable = false)
    private FournisseurMateriaux fournisseur;

    private String nom;
    private String categorie;
    private String unite; // sac, m3, ml, unite...
    private Long prixUnitaireFcfa;
    private Integer stockDisponible;
    private boolean actif = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
