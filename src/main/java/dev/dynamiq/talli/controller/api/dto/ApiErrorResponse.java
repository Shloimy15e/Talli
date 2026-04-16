package dev.dynamiq.talli.controller.api.dto;

import java.util.Map;

public record ApiErrorResponse(
        String error,
        Map<String, String> fieldErrors
) {}
