package com.gidajari.entity;

import com.gidajari.entity.enums.EscrowStatus;
import com.gidajari.entity.enums.NiveauCommission;
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
 * Entite P1 - Transaction d'escrow (sequestre).
 *
 * Regle de gestion n1 : les fonds transitent EXCLUSIVEMENT par ce compte
 * de sequestre Gida Jari. Aucun paiement direct entre les parties.
 * Regle n2 : commission preleve uniquement a la liberation effective des fonds.
 *
 * Composition: EscrowTransaction o-- EscrowMilestone.
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "escrow_transaction")
@Getter
@Setter
@NoArgsConstructor
public class EscrowTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @JsonIgnoreProperties({"jalons", "hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projet_id", nullable = false)
    private Projet projet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payeur_id", nullable = false)
    private User payeur;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beneficiaire_id", nullable = false)
    private User beneficiaire;

    private Long montantTotalFcfa;
    private Long montantLibereFcfa = 0L;

    @Enumerated(EnumType.STRING)
    private NiveauCommission niveauCommission = NiveauCommission.STANDARD;

    @Enumerated(EnumType.STRING)
    private EscrowStatus statut = EscrowStatus.DRAFT;

    private String operateurMobileMoney; // AIRTEL_MONEY, MOOV_MONEY, ZAMANI_CASH, NITA, AMANA

    /** Numero Mobile Money debite pour alimenter le sequestre. */
    private String numeroPayeur;

    /** Reference de la transaction cote operateur (collecte). */
    private String referencePaiement;

    /** Motif du litige, si ouvert (machine a etats : -> DISPUTED). */
    @Column(columnDefinition = "TEXT")
    private String motifLitige;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "litige_ouvert_par_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User litigeOuvertPar;

    @OneToMany(mappedBy = "escrowTransaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EscrowMilestone> milestones = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
