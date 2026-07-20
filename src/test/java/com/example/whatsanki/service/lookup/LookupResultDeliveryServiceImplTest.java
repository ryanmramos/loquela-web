package com.example.whatsanki.service.lookup;

import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.OutboundMessageId;
import com.example.whatsanki.domain.ProviderMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.WhatsAppAddress;
import com.example.whatsanki.dto.LookupDeliveryPartDto;
import com.example.whatsanki.dto.LookupDeliveryPreparationDto;
import com.example.whatsanki.dto.OutboundMessageDto;
import com.example.whatsanki.service.lookup.LookupResultDeliveryLifecycleService;
import com.example.whatsanki.service.twilio.OutboundMessenger;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LookupResultDeliveryServiceImplTest {
    private final LookupResultDeliveryLifecycleService lifecycleService =
            mock(LookupResultDeliveryLifecycleService.class);
    private final OutboundMessenger outboundMessenger = mock(OutboundMessenger.class);
    private final LookupResultDeliveryServiceImpl service =
            new LookupResultDeliveryServiceImpl(lifecycleService, outboundMessenger);

    @Test
    void resumesMultipartDeliveryWithoutResendingAcceptedParts() {
        UserId userId = UserId.random();
        LookupSessionId sessionId = LookupSessionId.random();
        WhatsAppAddress recipient = new WhatsAppAddress("whatsapp:+15551234567");
        LookupDeliveryPartDto accepted = new LookupDeliveryPartDto(
                OutboundMessageId.random(), 1, "first", true
        );
        LookupDeliveryPartDto pending = new LookupDeliveryPartDto(
                OutboundMessageId.random(), 2, "second", false
        );
        when(lifecycleService.prepare(any(), any(), any())).thenReturn(
                new LookupDeliveryPreparationDto(recipient, List.of(accepted, pending))
        );
        when(outboundMessenger.send(recipient, new OutboundMessageDto("second")))
                .thenReturn(new ProviderMessageId("SM0123456789abcdef0123456789abcdef"));

        var result = service.deliver(userId, sessionId);

        assertThat(result.newlyAcceptedParts()).isEqualTo(1);
        assertThat(result.totalParts()).isEqualTo(2);
        verify(outboundMessenger, never()).send(recipient, new OutboundMessageDto("first"));
        verify(lifecycleService).markProviderAccepted(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq(pending.outboundMessageId()),
                org.mockito.ArgumentMatchers.eq(new ProviderMessageId(
                        "SM0123456789abcdef0123456789abcdef"
                )),
                any(Instant.class)
        );
    }
}
