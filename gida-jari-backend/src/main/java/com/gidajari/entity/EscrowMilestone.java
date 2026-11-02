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
 * Entite P1 - Jalon de liberation des fonds d'escrow, lie a un Jalon technique.
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "escrow_milestone")
@Getter
@Setter
@NoArgsConstructor
public class EscrowMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "escrow_transaction_id", nullable = false)
    private EscrowTransaction escrowTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jalon_id")
    private Jalon jalon;

    private Long montantFcfa;
    /** Commission plateforme prelevee a la liberation (regle n2 - commission differee). */
    private Long commissionFcfa;

    /** Montant net verse au prestataire = montantFcfa - commissionFcfa. */
    private Long montantNetFcfa;

    private boolean libere = false;
    private Instant libereLe;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
