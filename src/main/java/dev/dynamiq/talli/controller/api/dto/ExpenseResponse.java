package dev.dynamiq.talli.controller.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseResponse(
        Long id,
        Long clientId,
        String clientName,
        Long projectId,
        String projectName,
        LocalDate incurredOn,
        BigDecimal amount,
        String currency,
        String category,
        String vendor,
        String description,
        Boolean billable
) {}
