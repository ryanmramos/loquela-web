package com.example.whatsanki.service.lookup;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.LookupSessionEntity;
import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ContextExampleDto;
import com.example.whatsanki.dto.DeliverLookupResultJobPayloadDto;
import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionaryProvenanceDto;
import com.example.whatsanki.dto.DictionarySenseDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LookupJobPayloadDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.LookupSessionRepository;
import com.example.whatsanki.service.job.BackgroundJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LookupSessionLifecycleServiceImplTest {

    @Mock private LookupSessionRepository lookupSessionRepository;
    @Mock private InboundMessageRepository inboundMessageRepository;
    @Mock private BackgroundJobService backgroundJobService;

    private WhatsAnkiConfigurationDto configuration;
    private LookupSessionLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        configuration = new WhatsAnkiConfigurationDto();
        configuration.getRetention().setLookupSession(Duration.ofMinutes(30));
        service = new LookupSessionLifecycleServiceImpl(
                lookupSessionRepository,
                inboundMessageRepository,
                backgroundJobService,
                configuration
        );
    }

    @Test
    void atomicallyCreatesPendingSessionWithLanguageSnapshotAndIdOnlyJob() {
        UserId userId = UserId.random();
        InboundMessageId inboundMessageId = InboundMessageId.random();
        Instant now = Instant.parse("2026-07-19T18:00:00Z");
        LanguagePairDto pair = new LanguagePairDto("es-MX", "en");
        when(lookupSessionRepository.findByUserIdAndInboundMessageId(
                userId.value(), inboundMessageId.value()
        )).thenReturn(Optional.empty());
        when(inboundMessageRepository.findByIdAndUserId(inboundMessageId.value(), userId.value()))
                .thenReturn(Optional.of(inbound(inboundMessageId, userId)));
        when(lookupSessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(backgroundJobService.enqueue(eq(userId), eq(BackgroundJobEntity.Type.LOOKUP), any()))
                .thenReturn(JobId.random());

        var result = service.accept(
                userId, inboundMessageId, "Vivir", "vivir", pair, now
        );

        ArgumentCaptor<LookupSessionEntity> saved = ArgumentCaptor.forClass(LookupSessionEntity.class);
        verify(lookupSessionRepository).save(saved.capture());
        assertThat(saved.getValue()).satisfies(session -> {
            assertThat(session.getId()).isEqualTo(result.lookupSessionId().value());
            assertThat(session.getSourceLanguage()).isEqualTo("es-MX");
            assertThat(session.getDefinitionLanguage()).isEqualTo("en");
            assertThat(session.getStatus()).isEqualTo(LookupSessionEntity.Status.PENDING);
            assertThat(session.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
        });
        verify(backgroundJobService).enqueue(
                userId,
                BackgroundJobEntity.Type.LOOKUP,
                LookupJobPayloadDto.create(result.lookupSessionId()).toMap()
        );
    }

    @Test
    void reusesInboundAcceptanceWithoutCreatingAnotherJob() {
        UserId userId = UserId.random();
        InboundMessageId inboundMessageId = InboundMessageId.random();
        LookupSessionEntity existing = pending(userId, LookupSessionId.random(), Instant.now().plusSeconds(60));
        existing.setInboundMessageId(inboundMessageId.value());
        when(lookupSessionRepository.findByUserIdAndInboundMessageId(
                userId.value(), inboundMessageId.value()
        )).thenReturn(Optional.of(existing));

        var result = service.accept(
                userId,
                inboundMessageId,
                "different retry text",
                "different retry text",
                new LanguagePairDto("en", "es"),
                Instant.now()
        );

        assertThat(result.lookupSessionId().value()).isEqualTo(existing.getId());
        assertThat(result.languagePair()).isEqualTo(new LanguagePairDto("es", "en"));
        verify(backgroundJobService, never()).enqueue(any(), any(), any());
    }

    @Test
    void completionCopiesPdfPageMetadataAndMarksSessionReady() {
        UserId userId = UserId.random();
        LookupSessionId sessionId = LookupSessionId.random();
        LookupSessionEntity session = pending(userId, sessionId, Instant.now().plusSeconds(600));
        when(lookupSessionRepository.findByIdAndUserId(sessionId.value(), userId.value()))
                .thenReturn(Optional.of(session));
        when(lookupSessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        boolean completed = service.complete(
                userId,
                sessionId,
                entry(),
                List.of(context(Map.of("pageNumber", 12))),
                Instant.now()
        );

        assertThat(completed).isTrue();
        assertThat(session.getStatus()).isEqualTo(LookupSessionEntity.Status.READY);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contexts =
                (List<Map<String, Object>>) session.getResultPayload().get("contexts");
        assertThat(contexts).singleElement().satisfies(context -> {
            assertThat(context).containsEntry("number", 1).containsEntry("sourceKind", "PDF");
            assertThat(map(context.get("location"))).containsEntry("pageNumber", 12);
        });
        verify(backgroundJobService).enqueue(
                userId,
                BackgroundJobEntity.Type.DELIVER_LOOKUP_RESULT,
                DeliverLookupResultJobPayloadDto.create(sessionId).toMap()
        );
    }

    @Test
    void marksAnExpiredReadySnapshotUnavailableWithoutDeletingItsPayload() {
        UserId userId = UserId.random();
        LookupSessionId sessionId = LookupSessionId.random();
        Instant now = Instant.parse("2026-07-19T18:00:00Z");
        LookupSessionEntity session = pending(userId, sessionId, now.minusSeconds(1));
        session.setStatus(LookupSessionEntity.Status.READY);
        session.setResultPayload(Map.of("schemaVersion", 1, "contexts", List.of(Map.of("number", 1))));
        when(lookupSessionRepository.findByIdAndUserId(sessionId.value(), userId.value()))
                .thenReturn(Optional.of(session));
        when(lookupSessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.markExpired(userId, sessionId, now);

        assertThat(session.getStatus()).isEqualTo(LookupSessionEntity.Status.EXPIRED);
        assertThat(session.getResultPayload()).containsKey("contexts");
        verify(lookupSessionRepository).save(session);
    }

    @Test
    void marksTerminalFailureAndQueuesOneDurableNotification() {
        UserId userId = UserId.random();
        LookupSessionId sessionId = LookupSessionId.random();
        Instant now = Instant.parse("2026-07-19T18:00:00Z");
        LookupSessionEntity session = pending(userId, sessionId, now.plusSeconds(600));
        when(lookupSessionRepository.findByIdAndUserId(sessionId.value(), userId.value()))
                .thenReturn(Optional.of(session));
        when(lookupSessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(backgroundJobService.enqueue(
                userId,
                BackgroundJobEntity.Type.DELIVER_LOOKUP_RESULT,
                DeliverLookupResultJobPayloadDto.create(sessionId).toMap()
        )).thenReturn(JobId.random());

        service.markFailed(userId, sessionId, "CONTEXT_NOT_FOUND", now);

        assertThat(session.getStatus()).isEqualTo(LookupSessionEntity.Status.FAILED);
        assertThat(session.getErrorCode()).isEqualTo("CONTEXT_NOT_FOUND");
        verify(backgroundJobService).enqueue(
                userId,
                BackgroundJobEntity.Type.DELIVER_LOOKUP_RESULT,
                DeliverLookupResultJobPayloadDto.create(sessionId).toMap()
        );

        service.markFailed(userId, sessionId, "LOOKUP_FAILED", now.plusSeconds(1));
        verify(backgroundJobService).enqueue(
                userId,
                BackgroundJobEntity.Type.DELIVER_LOOKUP_RESULT,
                DeliverLookupResultJobPayloadDto.create(sessionId).toMap()
        );
    }

    private InboundMessageEntity inbound(InboundMessageId inboundMessageId, UserId userId) {
        InboundMessageEntity message = new InboundMessageEntity();
        message.setId(inboundMessageId.value());
        message.setUserId(userId.value());
        message.setCommandType(InboundMessageEntity.CommandType.LOOKUP);
        return message;
    }

    private LookupSessionEntity pending(UserId userId, LookupSessionId sessionId, Instant expiresAt) {
        LookupSessionEntity session = new LookupSessionEntity();
        session.setId(sessionId.value());
        session.setUserId(userId.value());
        session.setRequestedTerm("vivir");
        session.setNormalizedTerm("vivir");
        session.setSourceLanguage("es");
        session.setDefinitionLanguage("en");
        session.setResultPayload(Map.of("schemaVersion", 1));
        session.setStatus(LookupSessionEntity.Status.PENDING);
        session.setCreatedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        session.setExpiresAt(expiresAt);
        return session;
    }

    private DictionaryEntryDto entry() {
        return new DictionaryEntryDto(
                "vivir", "vivir", List.of("vivo"),
                List.of(new DictionarySenseDto("verb", "to live", "fake:sense")),
                new LanguagePairDto("es", "en"),
                new DictionaryProvenanceDto(
                        "fake", "v1", "fake:vivir", "", "TEST", "Test data", Map.of()
                )
        );
    }

    private ContextExampleDto context(Map<String, Object> location) {
        return new ContextExampleDto(
                UUID.randomUUID(), SourceId.random(), SourceDocumentEntity.Kind.PDF,
                "Book", new LanguageTag("es"), 0, "Quiero vivir aquí.", location
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
