package com.example.whatsanki.service.lookup;

import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.OutboundMessageId;
import com.example.whatsanki.domain.ProviderMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.LookupDeliveryPreparationDto;

import java.time.Instant;

public interface LookupResultDeliveryLifecycleService {
    LookupDeliveryPreparationDto prepare(UserId userId, LookupSessionId lookupSessionId, Instant now);
    void markProviderAccepted(
            UserId userId,
            OutboundMessageId outboundMessageId,
            ProviderMessageId providerMessageId,
            Instant now
    );
    void markFailed(UserId userId, LookupSessionId lookupSessionId, String errorCode, Instant now);
}
