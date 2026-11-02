package com.gidajari.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Soumission de preuve photo geolocalisee GPS pour la validation d'un jalon (profil Artisan). */
public record JalonPreuveDto(
        @NotBlank String jalonId,
        @NotBlank String photoUrl,
        @NotNull Double latitude,
        @NotNull Double longitude
) {}
