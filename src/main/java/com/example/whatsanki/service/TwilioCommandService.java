package com.example.whatsanki.service;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.TwilioWebhookRequestDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;

public interface TwilioCommandService {
    InboundMessageEntity.CommandType classify(TwilioWebhookRequestDto request);

    TwilioWebhookResponseDto handle(TwilioWebhookRequestDto request);
}
