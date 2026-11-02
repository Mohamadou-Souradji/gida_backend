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
 * Profil 6 - Fournisseur de materiaux de construction
 * (quincaillerie, depot, cimenterie, revendeur de fer/bois).
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "fournisseur_materiaux")
@DiscriminatorValue("FOURNISSEUR_MATERIAUX")
@Getter
@Setter
@NoArgsConstructor
public class FournisseurMateriaux extends User {

    private String nomBoutique;
    private String nif;

    /** Categories de materiaux vendus, separees par virgule (V1 simplifiee). */
    private String categories;

    private String zonesLivraison;
    private Long fraisLivraisonFcfa;
}
