package com.example.whatsanki.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.util.List;

public record ApiProblemDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        URI type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        String detail,
        String instance,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String correlationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<FieldErrorDto> fieldErrors
) {
    public record FieldErrorDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String field,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message
    ) {
    }
}
