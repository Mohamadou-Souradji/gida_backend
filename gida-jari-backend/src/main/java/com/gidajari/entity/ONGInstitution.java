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
 * Profil 5 - ONG / Institution (education, sante, developpement local).
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "ong_institution")
@DiscriminatorValue("ONG_INSTITUTION")
@Getter
@Setter
@NoArgsConstructor
public class ONGInstitution extends User {

    private String raisonSociale;
    private String nifOuRccm;
    private String typeOrganisation;
    private String statutLegal;

    /** Objectif de Developpement Durable cible (ODD), le cas echeant. */
    private String oddCible;

    private String logoUrl;
}
