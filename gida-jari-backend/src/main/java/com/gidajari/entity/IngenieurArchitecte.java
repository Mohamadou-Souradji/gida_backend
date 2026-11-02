package com.gidajari.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Profil 4 - Ingenieur / Architecte / Conducteur des travaux.
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "ingenieur_architecte")
@DiscriminatorValue("INGENIEUR_ARCHITECTE")
@Getter
@Setter
@NoArgsConstructor
public class IngenieurArchitecte extends User {

    private String nom;
    private String prenom;

    /** Etudes, conception, controle de conformite, etc. */
    private String specialite;

    private String numeroOrdre;
    private String agrementMinisteriel;

    /** Grille tarifaire horaire et forfaitaire (JSON serialise en texte pour V1). */
    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String grilleTarifaireJson;
}
