package com.gidajari.entity;

import com.gidajari.entity.enums.PropositionEtudeStatus;
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
 * Proposition d'etude decoupee en phases, envoyee par un Ingenieur/Architecte
 * a un client pour un projet donne (roadmap 50% profil Ingenieur/Architecte,
 * Cahier des Charges Technique section 2).
 * Composition : PropositionEtude o-- PhaseEtude.
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "proposition_etude")
@Getter
@Setter
@NoArgsConstructor
public class PropositionEtude {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expert_id", nullable = false)
    private IngenieurArchitecte expert;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projet_id", nullable = false)
    private Projet projet;

    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Long montantTotalFcfa;

    @Enumerated(EnumType.STRING)
    private PropositionEtudeStatus statut = PropositionEtudeStatus.BROUILLON;

    @OneToMany(mappedBy = "proposition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PhaseEtude> phases = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant envoyeeLe;
}
