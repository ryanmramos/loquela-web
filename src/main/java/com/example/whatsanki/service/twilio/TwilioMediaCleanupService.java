package com.example.whatsanki.service.twilio;

import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.UserId;

public interface TwilioMediaCleanupService {
    void delete(UserId userId, InboundMessageId inboundMessageId);
}
