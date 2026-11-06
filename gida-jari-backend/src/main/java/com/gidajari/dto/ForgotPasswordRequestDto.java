package com.gidajari.dto;

/**
 * Demande de réinitialisation de mot de passe (envoi OTP).
 */
public record ForgotPasswordRequestDto(
        String email,
        String phone
) {
}
