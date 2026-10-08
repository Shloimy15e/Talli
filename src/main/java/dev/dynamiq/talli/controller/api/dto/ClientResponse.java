package dev.dynamiq.talli.controller.api.dto;

import java.math.BigDecimal;

public record ClientResponse(
        Long id,
        String name,
        String email,
        String phone,
        Integer paymentTermsDays,
        BigDecimal defaultHourlyRate
) {}
