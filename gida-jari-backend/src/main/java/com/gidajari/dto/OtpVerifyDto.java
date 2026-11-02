package com.gidajari.dto;

import com.gidajari.entity.enums.ProfileType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OtpVerifyDto(
        @NotBlank String phone,
        @NotBlank String code,
        /** Requis seulement a l'inscription (premiere verification pour ce numero). */
        ProfileType profileType
) {}
