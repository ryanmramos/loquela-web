package com.example.whatsanki.service;

import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.LookupDeliveryResultDto;

public interface LookupResultDeliveryService {
    LookupDeliveryResultDto deliver(UserId userId, LookupSessionId lookupSessionId);
    void markFailed(UserId userId, LookupSessionId lookupSessionId, String errorCode);
}
