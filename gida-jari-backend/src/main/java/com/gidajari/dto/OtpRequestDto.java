package com.gidajari.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OtpRequestDto(
        @NotBlank String phone,
        /** Requis a l'inscription (ou pour un ancien compte sans email) : le code y est envoye. */
        @Size(max = 160) String email
) {}
