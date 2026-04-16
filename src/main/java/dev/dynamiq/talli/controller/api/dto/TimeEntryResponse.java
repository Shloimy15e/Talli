package dev.dynamiq.talli.controller.api.dto;

import java.time.LocalDateTime;

public record TimeEntryResponse(
        Long id,
        Long projectId,
        String projectName,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Integer durationMinutes,
        String description,
        Boolean billable
) {}
