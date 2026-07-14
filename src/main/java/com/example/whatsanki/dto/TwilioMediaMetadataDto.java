package com.example.whatsanki.dto;

public record TwilioMediaMetadataDto(
        int count,
        String url,
        String contentType,
        String caption
) {
}
