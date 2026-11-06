package com.gidajari.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Demande de connexion avec email OU téléphone + mot de passe.
 */
public record LoginRequestDto(
        String email,
        String phone,

        @NotBlank(message = "Mot de passe requis")
        String password
) {
}
