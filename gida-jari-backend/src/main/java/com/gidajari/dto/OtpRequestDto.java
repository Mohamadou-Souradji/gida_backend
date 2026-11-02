package com.gidajari.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpRequestDto(
        @NotBlank String phone
) {}
