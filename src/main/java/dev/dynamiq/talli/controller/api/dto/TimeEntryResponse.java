package dev.dynamiq.talli.controller.api.dto;

public record TimeEntryResponse(
        Long id,
        Long projectId,
        String projectName,
        /** Epoch millis (UTC). Unambiguous — JS does `new Date(startedAt)` directly. */
        long startedAt,
        /** Epoch millis, or null if still running. */
        Long endedAt,
        Integer durationMinutes,
        String description,
        Boolean billable
) {}
