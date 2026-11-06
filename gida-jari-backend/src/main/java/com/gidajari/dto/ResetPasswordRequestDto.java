package com.gidajari.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Réinitialisation de mot de passe après vérification OTP.
 */
public record ResetPasswordRequestDto(
        @NotBlank(message = "Téléphone requis")
        String phone,

        @NotBlank(message = "Code OTP requis")
        String code,

        @NotBlank(message = "Nouveau mot de passe requis")
        @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
        String newPassword,

        @NotBlank(message = "Confirmation du nouveau mot de passe requise")
        String confirmPassword
) {
}
