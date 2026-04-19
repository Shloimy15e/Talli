package dev.dynamiq.talli.controller.api.dto;

public record TimerStatusResponse(
        Long id,
        Long projectId,
        String projectName,
        String description,
        /** Epoch millis of start (informational — may be affected by server clock/tz). */
        long startedAt,
        /**
         * Seconds elapsed as computed by the server at response time.
         * Client should use this as the baseline and increment locally,
         * avoiding any dependency on client/server clock sync.
         */
        long elapsedSeconds,
        boolean running
) {}
