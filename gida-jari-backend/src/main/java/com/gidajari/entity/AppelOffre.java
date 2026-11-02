package com.gidajari.entity;

import com.gidajari.entity.enums.AppelOffreStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entite P2 - Appel d'offre publie par une ONG/Institution.
 * Composition: AppelOffre o-- CandidatureAO.
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "appel_offre")
@Getter
@Setter
@NoArgsConstructor
public class AppelOffre {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private ONGInstitution institution;

    private String titre;

    @Column(columnDefinition = "TEXT")
    private String cahierDesChargesTexte;

    private Instant dateLimiteDepot;

    @Enumerated(EnumType.STRING)
    private AppelOffreStatus statut = AppelOffreStatus.BROUILLON;

    @JsonIgnore
    @OneToMany(mappedBy = "appelOffre", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CandidatureAO> candidatures = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
