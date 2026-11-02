package com.gidajari.entity;

import com.gidajari.entity.enums.KycLevel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Entite P1 - Soumission KYC (piece d'identite, justificatif, document
 * professionnel...) pour progresser dans les 7 niveaux de verification.
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "kyc_submission")
@Getter
@Setter
@NoArgsConstructor
public class KYCSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private KycLevel niveauVise;

    private String typeDocument; // CNI, passeport, NIF, RCCM, diplome, agrement...
    private String documentUrl;

    private String statut = "EN_ATTENTE"; // EN_ATTENTE, APPROUVE, REJETE

    private String commentaireValidation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "valide_par_admin_id")
    private User valideParAdmin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant traiteLe;
}
