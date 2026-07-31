package com.example.whatsanki.dto;

import java.time.Instant;

public record BrowserAccountDeletionDto(
        String status,
        String jobReference,
        Instant acceptedAt
) { }
