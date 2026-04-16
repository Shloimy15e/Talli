package dev.dynamiq.talli.controller.api.dto;

public record ProjectResponse(
        Long id,
        String name,
        Long clientId,
        String clientName,
        String status
) {}
