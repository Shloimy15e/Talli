package dev.dynamiq.talli.controller.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateProjectRequest(
        @NotBlank String name,
        @NotNull Long clientId,
        String rateType,
        @NotNull BigDecimal currentRate,
        String currency,
        String billingFrequency,
        Boolean billable
) {}
