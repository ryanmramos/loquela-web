package com.example.whatsanki.service;

import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.UserId;

public interface TwilioMediaCleanupService {
    void delete(UserId userId, InboundMessageId inboundMessageId);
}
