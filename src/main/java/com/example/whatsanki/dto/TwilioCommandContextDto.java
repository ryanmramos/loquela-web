package com.example.whatsanki.dto;

import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.UserId;

import java.util.Objects;

public record TwilioCommandContextDto(
        UserId userId,
        InboundMessageId inboundMessageId,
        ParsedTwilioCommandDto command
) {
    public TwilioCommandContextDto {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(inboundMessageId, "inboundMessageId must not be null");
        Objects.requireNonNull(command, "command must not be null");
    }
}
