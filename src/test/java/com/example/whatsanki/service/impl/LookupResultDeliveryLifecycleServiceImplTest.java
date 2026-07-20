package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.LookupSessionEntity;
import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.OutboundMessageEntity;
import com.example.whatsanki.domain.OutboundMessageId;
import com.example.whatsanki.domain.ProviderMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.WhatsAppAddress;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.LookupSessionRepository;
import com.example.whatsanki.repository.OutboundMessageRepository;
import com.example.whatsanki.service.IdentityProtectionService;
import com.example.whatsanki.service.LookupFailureMessageFormatter;
import com.example.whatsanki.service.LookupResultMessageFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LookupResultDeliveryLifecycleServiceImplTest {
    private final LookupSessionRepository lookupSessionRepository = mock(LookupSessionRepository.class);
    private final OutboundMessageRepository outboundMessageRepository = mock(OutboundMessageRepository.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final IdentityProtectionService identityProtectionService = mock(IdentityProtectionService.class);
    private final LookupResultMessageFormatter formatter = mock(LookupResultMessageFormatter.class);
    private final LookupFailureMessageFormatter failureFormatter = mock(LookupFailureMessageFormatter.class);
    private LookupResultDeliveryLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LookupResultDeliveryLifecycleServiceImpl(
                lookupSessionRepository,
                outboundMessageRepository,
                appUserRepository,
                identityProtectionService,
                formatter,
                failureFormatter
        );
    }

    @Test
    void createsStablePartRecordsAndReturnsDecryptedRecipient() {
        UserId userId = UserId.random();
        LookupSessionId sessionId = LookupSessionId.random();
        Instant now = Instant.parse("2026-07-19T20:00:00Z");
        LookupSessionEntity session = readySession(userId, sessionId, now.plusSeconds(300));
        AppUserEntity user = activeUser(userId);
        WhatsAppAddress recipient = new WhatsAppAddress("whatsapp:+15551234567");
        when(lookupSessionRepository.findByIdAndUserId(sessionId.value(), userId.value()))
                .thenReturn(Optional.of(session));
        when(formatter.format(session.getResultPayload())).thenReturn(List.of("one", "two"));
        when(outboundMessageRepository.findAllByUserIdAndLookupSessionIdOrderByPartNumber(
                userId.value(), sessionId.value()
        )).thenReturn(List.of());
        when(outboundMessageRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findByIdAndStatus(userId.value(), AppUserEntity.Status.ACTIVE))
                .thenReturn(Optional.of(user));
        when(identityProtectionService.decrypt(user.getWhatsappAddressCiphertext())).thenReturn(recipient);

        var preparation = service.prepare(userId, sessionId, now);

        assertThat(preparation.recipient()).isEqualTo(recipient);
        assertThat(preparation.parts()).extracting(part -> part.partNumber())
                .containsExactly(1, 2);
        assertThat(preparation.parts()).extracting(part -> part.body())
                .containsExactly("one", "two");
        assertThat(preparation.parts()).allMatch(part -> !part.providerAccepted());
    }

    @Test
    void recordsProviderAcceptanceIdempotently() {
        UserId userId = UserId.random();
        OutboundMessageEntity record = new OutboundMessageEntity();
        record.setId(java.util.UUID.randomUUID());
        record.setUserId(userId.value());
        record.setLookupSessionId(LookupSessionId.random().value());
        record.setPartNumber(1);
        record.setStatus(OutboundMessageEntity.Status.PENDING);
        when(outboundMessageRepository.findByIdAndUserId(record.getId(), userId.value()))
                .thenReturn(Optional.of(record));
        when(outboundMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Instant now = Instant.parse("2026-07-19T20:00:00Z");
        ProviderMessageId providerId = new ProviderMessageId(
                "SM0123456789abcdef0123456789abcdef"
        );

        service.markProviderAccepted(
                userId, new OutboundMessageId(record.getId()),
                providerId, now
        );

        assertThat(record.getStatus()).isEqualTo(OutboundMessageEntity.Status.PROVIDER_ACCEPTED);
        assertThat(record.getProviderMessageSid()).isEqualTo(providerId.value());
        assertThat(record.getProviderAcceptedAt()).isEqualTo(now);
        verify(outboundMessageRepository).save(record);
    }

    @Test
    void createsFailureNotificationFromSafeCodeUsingTheSameOutbox() {
        UserId userId = UserId.random();
        LookupSessionId sessionId = LookupSessionId.random();
        Instant now = Instant.parse("2026-07-19T20:00:00Z");
        LookupSessionEntity session = readySession(userId, sessionId, now.minusSeconds(1));
        session.setStatus(LookupSessionEntity.Status.FAILED);
        session.setErrorCode("DEFINITION_NOT_FOUND");
        AppUserEntity user = activeUser(userId);
        WhatsAppAddress recipient = new WhatsAppAddress("whatsapp:+15551234567");
        when(lookupSessionRepository.findByIdAndUserId(sessionId.value(), userId.value()))
                .thenReturn(Optional.of(session));
        when(failureFormatter.format("DEFINITION_NOT_FOUND"))
                .thenReturn("I couldn't find a definition for that term.");
        when(outboundMessageRepository.findAllByUserIdAndLookupSessionIdOrderByPartNumber(
                userId.value(), sessionId.value()
        )).thenReturn(List.of());
        when(outboundMessageRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findByIdAndStatus(userId.value(), AppUserEntity.Status.ACTIVE))
                .thenReturn(Optional.of(user));
        when(identityProtectionService.decrypt(user.getWhatsappAddressCiphertext())).thenReturn(recipient);

        var preparation = service.prepare(userId, sessionId, now);

        assertThat(preparation.parts()).singleElement().satisfies(part -> {
            assertThat(part.partNumber()).isEqualTo(1);
            assertThat(part.body()).contains("couldn't find a definition");
            assertThat(part.providerAccepted()).isFalse();
        });
    }

    private LookupSessionEntity readySession(
            UserId userId,
            LookupSessionId sessionId,
            Instant expiresAt
    ) {
        LookupSessionEntity session = new LookupSessionEntity();
        session.setId(sessionId.value());
        session.setUserId(userId.value());
        session.setRequestedTerm("vivir");
        session.setNormalizedTerm("vivir");
        session.setSourceLanguage("es");
        session.setDefinitionLanguage("en");
        session.setResultPayload(Map.of("schemaVersion", 1, "dictionary", Map.of(), "contexts", List.of()));
        session.setStatus(LookupSessionEntity.Status.READY);
        session.setCreatedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        session.setExpiresAt(expiresAt);
        return session;
    }

    private AppUserEntity activeUser(UserId userId) {
        AppUserEntity user = new AppUserEntity();
        user.setId(userId.value());
        user.setStatus(AppUserEntity.Status.ACTIVE);
        user.setWhatsappAddressCiphertext(new byte[]{1, 2, 3});
        return user;
    }
}
