package com.example.whatsanki.service;

import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.UserId;

public interface TwilioMediaCleanupScheduler {
    void enqueueIfNeeded(UserId userId, InboundMessageId inboundMessageId);
    int enqueuePending(int limit);
}
