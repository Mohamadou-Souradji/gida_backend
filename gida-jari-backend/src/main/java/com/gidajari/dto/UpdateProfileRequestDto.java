package com.gidajari.dto;

import com.gidajari.entity.enums.ProfileType;
import jakarta.validation.constraints.Size;

/**
 * DTO unifié pour la mise à jour de profil de tous les types d'utilisateurs.
 * Tous les champs sont optionnels - seuls les champs fournis seront mis à jour.
 * 
 * Usage:
 * - Création: profileType requis + champs spécifiques au profil
 * - Mise à jour: seuls les champs à modifier
 */
public record UpdateProfileRequestDto(
        // Type de profil (requis uniquement pour la création)
        ProfileType profileType,

        // Mot de passe (optionnel)
        @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
        String password,
        
        String confirmPassword,

        // ===== CLIENT_PARTICULIER =====
        String nom,
        String prenom,
        String adresse,
        String typeClient,

        // ===== ARTISAN =====
        // nom, prenom (déjà définis ci-dessus)
        String corpsDeMetier,
        String zoneIntervention,
        Long tarifJournalierFcfa,

        // ===== ENTREPRISE_BTP =====
        String nomCommercial,
        String nif,
        String rccm,
        String domaineActivite,
        Integer effectif,

        // ===== INGENIEUR_ARCHITECTE =====
        // nom, prenom (déjà définis ci-dessus)
        String specialite,
        String numeroOrdre,
        String agrementMinisteriel,
        String grilleTarifaireJson,

        // ===== FOURNISSEUR_MATERIAUX =====
        String nomBoutique,
        // nif (déjà défini ci-dessus)
        String categories,
        String zonesLivraison,
        Long fraisLivraisonFcfa,

        // ===== ONG_INSTITUTION =====
        String raisonSociale,
        String nifOuRccm,
        String typeOrganisation,
        String statutLegal,
        String oddCible,
        String logoUrl
) {
}
