package dev.dynamiq.talli.controller.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record CreateClientRequest(
        @NotBlank String name,
        String email,
        String phone,
        Integer paymentTermsDays,
        @DecimalMin("0.00") BigDecimal defaultHourlyRate
) {}
