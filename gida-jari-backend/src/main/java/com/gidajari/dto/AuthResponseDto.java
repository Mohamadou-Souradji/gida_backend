package com.gidajari.dto;

public record AuthResponseDto(
        String token,
        String userId,
        String profileType,
        String kycLevel,
        boolean admin,
        /** SUPER_ADMIN, OPERATEUR_KYC, GESTIONNAIRE_FINANCES, LECTEUR (null si non admin). */
        String adminRole
) {}
