package com.example.whatsanki.service.twilio;

import com.example.whatsanki.dto.TwilioWebhookRequestDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;

public interface TwilioWebhookService {
    TwilioWebhookResponseDto handle(TwilioWebhookRequestDto request);
}
