package dev.dynamiq.talli.controller.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateClientRequest(
        @NotBlank String name,
        String email,
        String phone,
        Integer paymentTermsDays
) {}
