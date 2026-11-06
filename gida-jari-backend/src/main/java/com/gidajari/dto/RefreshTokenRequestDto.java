package com.gidajari.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Demande de rafraîchissement du token d'accès.
 */
public record RefreshTokenRequestDto(
        @NotBlank(message = "Refresh token requis")
        String refreshToken
) {
}
