package com.gidajari.entity;

import com.gidajari.entity.enums.OtpChannel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Entite P1 - Verification OTP (authentification par numero de telephone).
 * Regle de gestion n5, Cahier des Charges Technique.
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "otp_verification")
@Getter
@Setter
@NoArgsConstructor
public class OTPVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 20)
    private String phone;

    /** Le code n'est jamais stocke en clair en production (hash a implementer). */
    @JsonIgnore
    @Column(nullable = false)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    private OtpChannel canal = OtpChannel.SMS;

    private boolean utilise = false;
    private int tentatives = 0;

    private Instant expireLe;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
