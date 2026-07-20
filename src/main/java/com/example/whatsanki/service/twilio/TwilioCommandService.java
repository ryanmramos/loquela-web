package com.example.whatsanki.service.twilio;

import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;

public interface TwilioCommandService {
    TwilioWebhookResponseDto handle(TwilioCommandContextDto context);
}
