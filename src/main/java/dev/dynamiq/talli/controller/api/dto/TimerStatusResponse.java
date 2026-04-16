package dev.dynamiq.talli.controller.api.dto;

import java.time.LocalDateTime;

public record TimerStatusResponse(
        Long id,
        Long projectId,
        String projectName,
        String description,
        LocalDateTime startedAt,
        boolean running
) {}
