package com.example.whatsanki.dto;

public record TwilioMediaDeletionPreparationDto(
        boolean alreadyDeleted,
        String messageSid,
        String mediaSid
) {
    public static TwilioMediaDeletionPreparationDto deleted() {
        return new TwilioMediaDeletionPreparationDto(true, null, null);
    }
}
