package dev.dynamiq.talli.controller.api.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateExpenseRequest(
        Long clientId,
        Long projectId,
        LocalDate incurredOn,
        @NotNull BigDecimal amount,
        String currency,
        @NotNull String category,
        String vendor,
        String description,
        String paymentMethod,
        Boolean billable
) {}
