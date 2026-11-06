package com.gidajari.dto;

/**
 * Token temporaire émis après vérification OTP pour permettre l'inscription.
 * Valide pendant 10 minutes.
 */
public record TemporaryTokenDto(
        String temporaryToken,
        String phone,
        String email
) {
}
