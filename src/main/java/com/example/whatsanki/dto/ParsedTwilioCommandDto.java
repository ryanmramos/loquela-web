package com.example.whatsanki.dto;

import com.example.whatsanki.domain.InboundMessageEntity;

public record ParsedTwilioCommandDto(
        InboundMessageEntity.CommandType commandType,
        String argument,
        String normalizedValue,
        TwilioMediaMetadataDto media,
        String validationError
) {
    public boolean isValid() {
        return validationError == null;
    }

    public boolean hasMedia() {
        return media != null && media.count() > 0;
    }
}
