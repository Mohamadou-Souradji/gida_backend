package com.gidajari.entity;

import com.gidajari.entity.enums.KycLevel;
import com.gidajari.entity.enums.ProfileType;
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
 * Superclasse unique User (entite P1 - critique).
 *
 * Contient les attributs partages par les 6 profils. L'identite unique
 * repose sur le numero de telephone + verification OTP SMS (regle de
 * gestion n5, Cahier des Charges Technique).
 *
 * Strategie d'heritage JOINED : une table "users" avec les colonnes
 * communes + une table par profil (client_particulier, artisan, ...)
 * jointe sur l'id. Voir Cahier des Charges, section 1.2.
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "profile_type", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
public abstract class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Jamais expose dans les profils publics (anti-contournement : les echanges passent par la plateforme). */
    @JsonIgnore
    @Column(name = "phone", nullable = false, unique = true, length = 20)
    private String phone;

    /** Recoit le code de connexion. Jamais expose dans les profils publics, comme le telephone. */
    @JsonIgnore
    @Column(name = "email", unique = true, length = 160)
    private String email;

    @JsonIgnore
    @Column(name = "email_verifie", nullable = false)
    private boolean emailVerifie = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_type", insertable = false, updatable = false)
    private ProfileType profileType;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_level", nullable = false)
    private KycLevel kycLevel = KycLevel.NIVEAU_0_NON_VERIFIE;

    @JsonIgnore
    @Column(name = "wallet_balance", nullable = false)
    private Long walletBalance = 0L; // en FCFA (centimes non utilises, montants entiers)

    @Column(name = "profile_score")
    private Double profileScore = 0.0;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "phone_verifie", nullable = false)
    private boolean phoneVerifie = false;

    @Column(name = "langue_preferee", length = 5)
    private String languePreferee = "fr"; // "fr" ou "ha" (haoussa)

    @JsonIgnore
    @OneToMany(mappedBy = "proprietaire", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PortfolioItem> portfolio = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
