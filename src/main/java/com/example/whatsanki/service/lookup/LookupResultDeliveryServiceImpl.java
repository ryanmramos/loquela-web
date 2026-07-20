package com.example.whatsanki.service.lookup;

import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.ProviderMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.LookupDeliveryPartDto;
import com.example.whatsanki.dto.LookupDeliveryPreparationDto;
import com.example.whatsanki.dto.LookupDeliveryResultDto;
import com.example.whatsanki.dto.OutboundMessageDto;
import com.example.whatsanki.service.exception.OutboundDeliveryException;
import com.example.whatsanki.service.twilio.OutboundMessenger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class LookupResultDeliveryServiceImpl implements LookupResultDeliveryService {
    private final LookupResultDeliveryLifecycleService lifecycleService;
    private final OutboundMessenger outboundMessenger;

    @Override
    public LookupDeliveryResultDto deliver(UserId userId, LookupSessionId lookupSessionId) {
        LookupDeliveryPreparationDto preparation = lifecycleService.prepare(
                userId, lookupSessionId, Instant.now()
        );
        int newlyAccepted = 0;
        for (LookupDeliveryPartDto part : preparation.parts()) {
            if (part.providerAccepted()) {
                continue;
            }
            try {
                ProviderMessageId providerMessageId = outboundMessenger.send(
                        preparation.recipient(), new OutboundMessageDto(part.body())
                );
                lifecycleService.markProviderAccepted(
                        userId, part.outboundMessageId(), providerMessageId, Instant.now()
                );
                newlyAccepted++;
            } catch (OutboundDeliveryException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw OutboundDeliveryException.transientFailure(
                        "OUTBOUND_DELIVERY_FAILED", exception
                );
            }
        }
        log.info(
                "Delivered lookup outcome userId={} lookupSessionId={} newlyAcceptedParts={} totalParts={}",
                userId.value(), lookupSessionId.value(), newlyAccepted, preparation.parts().size()
        );
        return new LookupDeliveryResultDto(newlyAccepted, preparation.parts().size());
    }

    @Override
    public void markFailed(UserId userId, LookupSessionId lookupSessionId, String errorCode) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        lifecycleService.markFailed(userId, lookupSessionId, errorCode, Instant.now());
    }
}
