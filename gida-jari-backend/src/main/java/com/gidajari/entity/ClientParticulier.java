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
 * Profil 1 - Client / Maitre d'ouvrage.
 * Voir Cahier des Charges Technique, section 1.2 et Document de Reference, Profil 1.
 */
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "client_particulier")
@DiscriminatorValue("CLIENT_PARTICULIER")
@Getter
@Setter
@NoArgsConstructor
public class ClientParticulier extends User {

    private String nom;
    private String prenom;
    private String adresse;

    /** Ex: particulier, institutionnel-leger, promoteur occasionnel. */
    private String typeClient;
}
