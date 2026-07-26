package com.example.whatsanki.database;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.LookupSessionEntity;
import com.example.whatsanki.domain.PassageEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LookupExecutionResultDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.LookupSessionRepository;
import com.example.whatsanki.repository.PassageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.lookup.LookupService;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LookupIntegrationTest extends PostgreSqlIntegrationTest {

    private final AppUserRepository appUserRepository;
    private final BackgroundJobRepository backgroundJobRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final PassageRepository passageRepository;
    private final LookupSessionRepository lookupSessionRepository;
    private final LookupService lookupService;

    LookupIntegrationTest(
            AppUserRepository appUserRepository,
            BackgroundJobRepository backgroundJobRepository,
            InboundMessageRepository inboundMessageRepository,
            SourceDocumentRepository sourceDocumentRepository,
            PassageRepository passageRepository,
            LookupSessionRepository lookupSessionRepository,
            LookupService lookupService
    ) {
        this.appUserRepository = appUserRepository;
        this.backgroundJobRepository = backgroundJobRepository;
        this.inboundMessageRepository = inboundMessageRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.passageRepository = passageRepository;
        this.lookupSessionRepository = lookupSessionRepository;
        this.lookupService = lookupService;
    }

    @Test
    void lookupUsesSnapshottedPairAndStoresOnlyMatchingReadyOwnedContextWithPdfPage() {
        AppUserEntity owner = user("lookup-owner", "es", "en");
        AppUserEntity other = user("lookup-other", "es", "en");
        SourceDocumentEntity matchingPdf = source(
                owner, "Spanish PDF", "es", SourceDocumentEntity.Status.READY, SourceDocumentEntity.Kind.PDF
        );
        SourceDocumentEntity wrongLanguage = source(
                owner, "English PDF", "en", SourceDocumentEntity.Status.READY, SourceDocumentEntity.Kind.PDF
        );
        SourceDocumentEntity notReady = source(
                owner, "Processing PDF", "es", SourceDocumentEntity.Status.INDEXING, SourceDocumentEntity.Kind.PDF
        );
        SourceDocumentEntity otherUsersPdf = source(
                other, "Other PDF", "es", SourceDocumentEntity.Status.READY, SourceDocumentEntity.Kind.PDF
        );
        passage(owner, matchingPdf, "Yo vivo aquí.", Map.of("pageNumber", 7));
        passage(owner, wrongLanguage, "I live here.", Map.of("pageNumber", 2));
        passage(owner, notReady, "Yo vivo todavía.", Map.of("pageNumber", 3));
        passage(other, otherUsersPdf, "Yo vivo allá.", Map.of("pageNumber", 4));
        InboundMessageEntity inbound = inbound(owner);
        UserId ownerId = new UserId(owner.getId());

        var accepted = lookupService.accept(
                ownerId, new InboundMessageId(inbound.getId()), "vivir"
        );

        // A concurrent LANGUAGE command after acceptance must not redirect this job.
        owner.setSourceLanguage("en");
        owner.setDefinitionLanguage("es");
        appUserRepository.saveAndFlush(owner);
        LookupExecutionResultDto executed = lookupService.execute(ownerId, accepted.lookupSessionId());

        assertThat(sourceDocumentRepository
                .findByIdAndUserId(matchingPdf.getId(), owner.getId())
                .orElseThrow()
                .getLanguage())
                .as("existing sources retain the language captured at acceptance")
                .isEqualTo("es");

        InboundMessageEntity futureInbound = inbound(owner);
        var futureLookup = lookupService.accept(
                ownerId, new InboundMessageId(futureInbound.getId()), "live"
        );
        assertThat(futureLookup.languagePair()).isEqualTo(new LanguagePairDto("en", "es"));

        // This test invokes the worker service directly, so finalize its queued fixture
        // job to avoid leaking eligible work into other shared-context integration tests.
        List<BackgroundJobEntity> lookupJobs = backgroundJobRepository.findRecentByUserId(
                        owner.getId(), 10
                ).stream()
                .filter(job -> job.getType() == BackgroundJobEntity.Type.LOOKUP
                        || job.getType() == BackgroundJobEntity.Type.DELIVER_LOOKUP_RESULT)
                .filter(job -> List.of(accepted.lookupSessionId(), futureLookup.lookupSessionId()).stream()
                        .map(id -> id.value().toString())
                        .anyMatch(id -> id.equals(String.valueOf(job.getPayload().get("lookupSessionId")))))
                .toList();
        assertThat(lookupJobs).hasSize(3);
        lookupJobs.forEach(job -> {
            job.setState(BackgroundJobEntity.State.CANCELLED);
            backgroundJobRepository.save(job);
        });

        assertThat(executed.outcome()).isEqualTo(LookupExecutionResultDto.Outcome.COMPLETED);
        assertThat(executed.contextCount()).isEqualTo(1);
        LookupSessionEntity session = lookupSessionRepository
                .findByIdAndUserId(accepted.lookupSessionId().value(), owner.getId())
                .orElseThrow();
        assertThat(session.getStatus()).isEqualTo(LookupSessionEntity.Status.READY);
        assertThat(session.getSourceLanguage()).isEqualTo("es");
        assertThat(session.getDefinitionLanguage()).isEqualTo("en");
        assertThat(session.getExpiresAt()).isAfter(session.getCreatedAt());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contexts =
                (List<Map<String, Object>>) session.getResultPayload().get("contexts");
        assertThat(contexts).singleElement().satisfies(context -> {
            assertThat(context)
                    .containsEntry("number", 1)
                    .containsEntry("sourceId", matchingPdf.getId().toString())
                    .containsEntry("sourceKind", "PDF")
                    .containsEntry("sourceLanguage", "es");
            assertThat(map(context.get("location"))).containsEntry("pageNumber", 7);
        });
        assertThat(lookupSessionRepository
                .findFirstByUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                        owner.getId(), LookupSessionEntity.Status.READY, Instant.now()
                )).get().extracting(LookupSessionEntity::getId).isEqualTo(session.getId());
    }

    private AppUserEntity user(String label, String sourceLanguage, String definitionLanguage) {
        AppUserEntity user = new AppUserEntity();
        user.setSourceLanguage(sourceLanguage);
        user.setDefinitionLanguage(definitionLanguage);
        user.setStatus(AppUserEntity.Status.ACTIVE);
        return appUserRepository.saveAndFlush(user);
    }

    private InboundMessageEntity inbound(AppUserEntity owner) {
        InboundMessageEntity message = new InboundMessageEntity();
        message.setTwilioMessageSid("SM" + UUID.randomUUID().toString().replace("-", ""));
        message.setUserId(owner.getId());
        message.setCommandType(InboundMessageEntity.CommandType.LOOKUP);
        message.setReceivedAt(Instant.now());
        return inboundMessageRepository.save(message);
    }

    private SourceDocumentEntity source(
            AppUserEntity owner,
            String name,
            String language,
            SourceDocumentEntity.Status status,
            SourceDocumentEntity.Kind kind
    ) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setUserId(owner.getId());
        source.setKind(kind);
        source.setDisplayName(name);
        source.setStatus(status);
        source.setDetectedMediaType("application/pdf");
        source.setLanguage(language);
        source.setCharacterCount(20L);
        source.setPassageCount(1);
        source.setMetadata(new LinkedHashMap<>());
        return sourceDocumentRepository.save(source);
    }

    private void passage(
            AppUserEntity owner,
            SourceDocumentEntity source,
            String text,
            Map<String, Object> location
    ) {
        PassageEntity passage = new PassageEntity();
        passage.setUserId(owner.getId());
        passage.setSourceId(source.getId());
        passage.setOrdinal(0);
        passage.setDisplayText(text);
        passage.setNormalizedText(text.toLowerCase());
        passage.setLocation(new LinkedHashMap<>(location));
        passage.setTokenCount(3);
        passageRepository.save(passage);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
