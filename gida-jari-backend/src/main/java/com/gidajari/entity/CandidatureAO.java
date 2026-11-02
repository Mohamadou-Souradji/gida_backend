package com.gidajari.entity;

import com.gidajari.entity.enums.CandidatureStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Entite P2 - Candidature technique/commerciale a un appel d'offre. */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "candidature_ao")
@Getter
@Setter
@NoArgsConstructor
public class CandidatureAO {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @JsonIgnoreProperties({"candidatures", "hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appel_offre_id", nullable = false)
    private AppelOffre appelOffre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidat_id", nullable = false)
    private User candidat; // EntrepriseBTP en priorite (voir CDC section 2, profil 4)

    @Column(columnDefinition = "TEXT")
    private String propositionTechnique;

    private Long montantPropositionFcfa;

    @Enumerated(EnumType.STRING)
    private CandidatureStatus statut = CandidatureStatus.SOUMISE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
