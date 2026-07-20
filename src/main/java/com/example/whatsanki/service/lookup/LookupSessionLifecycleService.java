package com.example.whatsanki.service.lookup;

import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LookupAcceptanceDto;
import com.example.whatsanki.dto.LookupExecutionPreparationDto;
import com.example.whatsanki.dto.ContextExampleDto;

import java.time.Instant;
import java.util.List;

/** Transactional persistence boundary around slow, non-transactional lookup orchestration. */
public interface LookupSessionLifecycleService {

    LookupAcceptanceDto accept(
            UserId userId,
            InboundMessageId inboundMessageId,
            String requestedTerm,
            String normalizedTerm,
            LanguagePairDto languagePair,
            Instant now
    );

    LookupExecutionPreparationDto prepare(UserId userId, LookupSessionId lookupSessionId, Instant now);

    boolean complete(
            UserId userId,
            LookupSessionId lookupSessionId,
            DictionaryEntryDto entry,
            List<ContextExampleDto> contexts,
            Instant now
    );

    void markExpired(UserId userId, LookupSessionId lookupSessionId, Instant now);

    void markFailed(UserId userId, LookupSessionId lookupSessionId, String errorCode, Instant now);
}
