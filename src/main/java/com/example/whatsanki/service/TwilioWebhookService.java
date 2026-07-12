package com.example.whatsanki.service;

import com.example.whatsanki.dto.TwilioWebhookRequestDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;

public interface TwilioWebhookService {
    TwilioWebhookResponseDto handle(TwilioWebhookRequestDto request);
}
