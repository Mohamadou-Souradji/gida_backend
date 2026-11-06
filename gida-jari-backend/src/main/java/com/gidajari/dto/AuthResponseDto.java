package com.gidajari.dto;

/**
 * Réponse d'authentification (après login ou register réussi) : access token + refresh token + métadonnées.
 * Le token porte : id utilisateur, profileType, admin.
 */
public record AuthResponseDto(
        String accessToken,
        String refreshToken,
        String userId,
        String profileType,
        String kycLevel,
        boolean admin,
        String adminRole
) {
}
