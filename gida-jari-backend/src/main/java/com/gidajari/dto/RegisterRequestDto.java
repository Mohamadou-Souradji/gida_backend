package com.gidajari.dto;

import com.gidajari.entity.enums.ProfileType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Demande d'inscription - Informations de base communes à tous les profils.
 * Nécessite un token temporaire obtenu après vérification OTP.
 * Après cette étape, l'utilisateur peut compléter son profil avec les infos spécifiques.
 */
public record RegisterRequestDto(
        @NotBlank(message = "Token temporaire requis")
        String temporaryToken,

        @NotNull(message = "Type de profil requis")
        ProfileType profileType,

        @NotBlank(message = "Nom requis")
        @Size(max = 50, message = "Nom trop long")
        String nom,

        @NotBlank(message = "Prénom requis")
        @Size(max = 50, message = "Prénom trop long")
        String prenom,

        @NotBlank(message = "Adresse requise")
        @Size(max = 200, message = "Adresse trop longue")
        String adresse,

        @NotBlank(message = "Mot de passe requis")
        @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
        String password,

        @NotBlank(message = "Confirmation du mot de passe requise")
        String confirmPassword
) {
}
