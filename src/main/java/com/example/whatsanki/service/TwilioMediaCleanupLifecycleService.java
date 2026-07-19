package com.example.whatsanki.service;

import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.TwilioMediaDeletionPreparationDto;

public interface TwilioMediaCleanupLifecycleService {
    TwilioMediaDeletionPreparationDto prepare(UserId userId, InboundMessageId inboundMessageId);
    void complete(UserId userId, InboundMessageId inboundMessageId, String mediaSid);
}
