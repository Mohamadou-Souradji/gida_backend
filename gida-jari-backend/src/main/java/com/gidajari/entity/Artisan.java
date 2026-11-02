package com.gidajari.entity;

import com.gidajari.entity.enums.DisponibiliteStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Profil 2 - Artisan (macon, plombier, electricien, menuisier, carreleur, peintre...).
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "artisan")
@DiscriminatorValue("ARTISAN")
@Getter
@Setter
@NoArgsConstructor
public class Artisan extends User {

    private String nom;
    private String prenom;

    /** Corps de metier: macon, plombier, electricien, menuisier, carreleur, peintre, etc. */
    private String corpsDeMetier;

    private String zoneIntervention;

    private Long tarifJournalierFcfa;

    @Enumerated(EnumType.STRING)
    private DisponibiliteStatus disponibilite = DisponibiliteStatus.UNAVAILABLE;

    /** Badge de reputation calcule (ex: NOUVEAU, VERIFIE, TOP_ARTISAN). */
    private String badge;
}
