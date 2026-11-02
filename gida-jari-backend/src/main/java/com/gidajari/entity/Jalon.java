package com.gidajari.entity;

import com.gidajari.entity.enums.JalonStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Entite P1 - Jalon technique d'un projet.
 * Un jalon soumis par le prestataire et non traite par le client sous 48h
 * est auto-valide (regle de gestion n3, tache @Scheduled).
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "jalon")
@Getter
@Setter
@NoArgsConstructor
public class Jalon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projet_id", nullable = false)
    private Projet projet;

    private String libelle;
    private Integer ordre;

    @Enumerated(EnumType.STRING)
    private JalonStatus statut = JalonStatus.A_FAIRE;

    private Long montantFcfa;

    /** Photo(s) horodatee(s) + geolocalisation GPS (URL(s), separees par virgule en V1). */
    private String preuvePhotoUrls;
    private Double latitude;
    private Double longitude;

    /** Motif saisi par le client lors d'un rejet de preuve (le prestataire peut resoumettre). */
    @Column(columnDefinition = "TEXT")
    private String commentaireClient;

    private Instant soumisLe;
    private Instant valideLe;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Expose l'id du projet sans serialiser tout le projet (evite les cycles JSON). */
    @JsonProperty("projetId")
    public String idDuProjet() {
        return projet != null ? projet.getId() : null;
    }
}
