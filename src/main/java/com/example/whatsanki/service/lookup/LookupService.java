package com.example.whatsanki.service.lookup;

import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.LookupAcceptanceDto;
import com.example.whatsanki.dto.LookupExecutionResultDto;

public interface LookupService {

    LookupAcceptanceDto accept(UserId userId, InboundMessageId inboundMessageId, String requestedTerm);

    LookupExecutionResultDto execute(UserId userId, LookupSessionId lookupSessionId);

    void markFailed(UserId userId, LookupSessionId lookupSessionId, String errorCode);
}
