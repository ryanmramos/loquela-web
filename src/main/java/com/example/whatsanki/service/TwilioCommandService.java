package com.example.whatsanki.service;

import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;

public interface TwilioCommandService {
    TwilioWebhookResponseDto handle(ParsedTwilioCommandDto command);
}
