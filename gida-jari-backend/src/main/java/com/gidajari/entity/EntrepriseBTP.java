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
 * Profil 3 - Entreprise BTP (structure encadrant plusieurs corps de metier).
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "entreprise_btp")
@DiscriminatorValue("ENTREPRISE_BTP")
@Getter
@Setter
@NoArgsConstructor
public class EntrepriseBTP extends User {

    private String nomCommercial;
    private String nif;
    private String rccm;
    private String domaineActivite;
    private Integer effectif;
}
