package dev.dynamiq.talli.controller.api.dto;

import jakarta.validation.constraints.NotNull;

public record StartTimerRequest(
        @NotNull Long projectId,
        String description
) {}
