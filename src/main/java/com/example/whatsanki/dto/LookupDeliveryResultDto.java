package com.example.whatsanki.dto;

public record LookupDeliveryResultDto(int newlyAcceptedParts, int totalParts) {
    public LookupDeliveryResultDto {
        if (newlyAcceptedParts < 0 || totalParts < 1 || newlyAcceptedParts > totalParts) {
            throw new IllegalArgumentException("Invalid lookup delivery counts");
        }
    }
}
