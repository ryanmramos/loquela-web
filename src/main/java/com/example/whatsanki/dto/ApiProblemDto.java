package com.example.whatsanki.dto;

import java.net.URI;
import java.util.List;

public record ApiProblemDto(
        URI type,
        String title,
        int status,
        String detail,
        String instance,
        String correlationId,
        List<FieldViolationDto> violations
) {
    public record FieldViolationDto(String field, String message) {
    }
}
