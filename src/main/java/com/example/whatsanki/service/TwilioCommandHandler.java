package com.example.whatsanki.service;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;

public interface TwilioCommandHandler {
    InboundMessageEntity.CommandType supportedCommand();

    TwilioWebhookResponseDto handle(TwilioCommandContextDto context);
}
