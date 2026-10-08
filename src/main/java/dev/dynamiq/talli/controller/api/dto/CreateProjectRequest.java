package dev.dynamiq.talli.controller.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record CreateProjectRequest(
        @NotBlank String name,
        @NotNull Long clientId,
        String rateType,
        @DecimalMin("0.00") BigDecimal currentRate,
        String currency,
        String billingFrequency,
        Boolean billable
) {}
