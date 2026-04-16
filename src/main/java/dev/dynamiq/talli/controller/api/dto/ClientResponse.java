package dev.dynamiq.talli.controller.api.dto;

public record ClientResponse(
        Long id,
        String name,
        String email,
        String phone,
        Integer paymentTermsDays
) {}
