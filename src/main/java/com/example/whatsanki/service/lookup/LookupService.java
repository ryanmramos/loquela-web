package com.example.whatsanki.service.lookup;

import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.LookupAcceptanceDto;
import com.example.whatsanki.dto.LookupExecutionResultDto;
import com.example.whatsanki.dto.LookupRequestFiltersDto;

public interface LookupService {

    LookupAcceptanceDto accept(UserId userId, InboundMessageId inboundMessageId, String requestedTerm);
    LookupAcceptanceDto acceptBrowser(UserId userId, String requestedTerm, LookupRequestFiltersDto filters);

    LookupExecutionResultDto execute(UserId userId, LookupSessionId lookupSessionId);

    void markFailed(UserId userId, LookupSessionId lookupSessionId, String errorCode);
}
