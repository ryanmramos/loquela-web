package com.example.whatsanki.dto;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponseDto(
        Instant timestamp,
        int status,
        String error,
        List<String> details
) {
}
