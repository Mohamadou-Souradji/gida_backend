package com.gidajari.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Demande de changement de mot de passe (utilisateur connecté).
 */
public record ChangePasswordRequestDto(
        @NotBlank(message = "Ancien mot de passe requis")
        String oldPassword,

        @NotBlank(message = "Nouveau mot de passe requis")
        @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
        String newPassword,

        @NotBlank(message = "Confirmation du nouveau mot de passe requise")
        String confirmPassword
) {
}
