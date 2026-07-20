package com.example.whatsanki.service.twilio;

import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioWebhookRequestDto;

public interface TwilioCommandParser {
    ParsedTwilioCommandDto parse(TwilioWebhookRequestDto request);
}
