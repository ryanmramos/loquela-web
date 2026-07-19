package com.example.whatsanki.dto;

public record TwilioMediaReferenceDto(String messageSid, String mediaSid) {
    public TwilioMediaReferenceDto {
        if (messageSid == null || !messageSid.matches("(?:SM|MM)[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("messageSid must be a Twilio Message SID");
        }
        if (mediaSid == null || !mediaSid.matches("ME[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("mediaSid must be a Twilio Media SID");
        }
    }
}
