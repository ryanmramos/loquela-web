package com.example.whatsanki.database;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.CardEntity;
import com.example.whatsanki.domain.DeckEntity;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.PassageEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.CorpusSearchOptionsDto;
import com.example.whatsanki.dto.IngestTextJobPayloadDto;
import com.example.whatsanki.dto.SourceDeletionRequestDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.repository.CardRepository;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.PassageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.SourceDeletionService;
import com.example.whatsanki.service.SourceManagementService;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.service.lookup.CorpusSearch;
import com.example.whatsanki.service.util.SourceReferenceCodec;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CorpusAndSourceManagementIntegrationTest extends PostgreSqlIntegrationTest {

    private final AppUserRepository appUserRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final PassageRepository passageRepository;
    private final BackgroundJobRepository backgroundJobRepository;
    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final BackgroundJobService backgroundJobService;
    private final CorpusSearch corpusSearch;
    private final SourceManagementService sourceManagementService;
    private final SourceDeletionService sourceDeletionService;

    CorpusAndSourceManagementIntegrationTest(
            AppUserRepository appUserRepository,
            InboundMessageRepository inboundMessageRepository,
            SourceDocumentRepository sourceDocumentRepository,
            PassageRepository passageRepository,
            BackgroundJobRepository backgroundJobRepository,
            DeckRepository deckRepository,
            CardRepository cardRepository,
            BackgroundJobService backgroundJobService,
            CorpusSearch corpusSearch,
            SourceManagementService sourceManagementService,
            SourceDeletionService sourceDeletionService
    ) {
        this.appUserRepository = appUserRepository;
        this.inboundMessageRepository = inboundMessageRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.passageRepository = passageRepository;
        this.backgroundJobRepository = backgroundJobRepository;
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.backgroundJobService = backgroundJobService;
        this.corpusSearch = corpusSearch;
        this.sourceManagementService = sourceManagementService;
        this.sourceDeletionService = sourceDeletionService;
    }

    @Test
    void searchIsExactRankedDiverseLanguageAwareReadyOnlyAndTenantScoped() {
        UserId owner = user("search-owner", "es");
        UserId other = user("search-other", "es");
        SourceDocumentEntity sourceA = source(owner, "Source A", "es", SourceDocumentEntity.Status.READY, null);
        SourceDocumentEntity sourceB = source(owner, "Source B", "es", SourceDocumentEntity.Status.READY, null);
        SourceDocumentEntity english = source(owner, "English", "en", SourceDocumentEntity.Status.READY, null);
        SourceDocumentEntity processing = source(owner, "Processing", "es", SourceDocumentEntity.Status.INDEXING, null);
        SourceDocumentEntity otherUser = source(other, "Other user", "es", SourceDocumentEntity.Status.READY, null);
        passage(owner, sourceA, 0, "árbol árbol árbol");
        passage(owner, sourceA, 1, "árbol árbol");
        passage(owner, sourceB, 0, "un árbol");
        passage(owner, english, 0, "árbol in an English source");
        passage(owner, processing, 0, "árbol still processing");
        passage(other, otherUser, 0, "árbol árbol árbol árbol");

        var results = corpusSearch.findExamples(
                owner,
                "ÁRBOL",
                new CorpusSearchOptionsDto(new LanguageTag("es"), 3, 2)
        );

        assertThat(results).extracting(result -> result.sourceDisplayName())
                .containsExactly("Source A", "Source B", "Source A");
        assertThat(results).extracting(result -> result.displayText())
                .containsExactly("árbol árbol árbol", "un árbol", "árbol árbol");
        assertThat(corpusSearch.findExamples(
                owner,
                "árb",
                new CorpusSearchOptionsDto(new LanguageTag("es"), 5, 2)
        )).isEmpty();
        assertThat(corpusSearch.findExamples(
                other,
                "árbol",
                new CorpusSearchOptionsDto(new LanguageTag("es"), 5, 2)
        )).extracting(result -> result.sourceDisplayName()).containsExactly("Other user");
    }

    @Test
    void sourceDeletionCancelsWorkClearsStagingCascadesPassagesAndIsIdempotent() {
        UserId userId = user("delete-owner", "es");
        InboundMessageEntity inbound = inbound(userId);
        SourceDocumentEntity source = source(
                userId,
                "Pasted text",
                "es",
                SourceDocumentEntity.Status.READY,
                inbound.getId()
        );
        passage(userId, source, 0, "texto eliminable");
        CardEntity preservedCard = card(userId, source);
        var ingestJobId = backgroundJobService.enqueue(
                userId,
                BackgroundJobEntity.Type.INGEST_TEXT,
                IngestTextJobPayloadDto.create(new SourceId(source.getId())).toMap()
        );

        SourceDeletionRequestDto request = sourceManagementService.requestDeletion(
                userId,
                SourceReferenceCodec.encode(new SourceId(source.getId()))
        );

        assertThat(request.outcome()).isEqualTo(SourceDeletionRequestDto.Outcome.QUEUED);
        assertThat(backgroundJobRepository.findByIdAndUserId(ingestJobId.value(), userId.value()))
                .get()
                .extracting(BackgroundJobEntity::getState)
                .isEqualTo(BackgroundJobEntity.State.CANCELLED);

        assertThat(sourceDeletionService.delete(userId, new SourceId(source.getId()), request.jobId())).isTrue();
        BackgroundJobEntity deletionJob = backgroundJobRepository
                .findByIdAndUserId(request.jobId().value(), userId.value())
                .orElseThrow();
        deletionJob.setState(BackgroundJobEntity.State.SUCCEEDED);
        backgroundJobRepository.save(deletionJob);

        assertThat(sourceDocumentRepository.findByIdAndUserId(source.getId(), userId.value())).isEmpty();
        assertThat(passageRepository.findAllByUserIdAndSourceIdOrderByOrdinal(userId.value(), source.getId()))
                .isEmpty();
        assertThat(inboundMessageRepository.findByIdAndUserId(inbound.getId(), userId.value()))
                .get()
                .extracting(InboundMessageEntity::getBodyCiphertext)
                .isNull();
        assertThat(cardRepository.findByIdAndUserId(preservedCard.getId(), userId.value()))
                .get()
                .extracting(CardEntity::getSourceId)
                .isNull();
        assertThat(sourceDeletionService.delete(userId, new SourceId(source.getId()), request.jobId())).isFalse();
        assertThat(sourceManagementService.requestDeletion(
                userId, SourceReferenceCodec.encode(new SourceId(source.getId()))
        ).outcome()).isEqualTo(SourceDeletionRequestDto.Outcome.NOT_FOUND_OR_DELETED);
        assertThat(corpusSearch.findExamples(
                userId,
                "eliminable",
                new CorpusSearchOptionsDto(new LanguageTag("es"), 5, 2)
        )).isEmpty();
    }

    private UserId user(String label, String sourceLanguage) {
        String identity = label + "-" + UUID.randomUUID();
        AppUserEntity user = new AppUserEntity();
        user.setSourceLanguage(sourceLanguage);
        user.setDefinitionLanguage("en");
        user.setStatus(AppUserEntity.Status.ACTIVE);
        return new UserId(appUserRepository.saveAndFlush(user).getId());
    }

    private InboundMessageEntity inbound(UserId userId) {
        InboundMessageEntity message = new InboundMessageEntity();
        message.setTwilioMessageSid("SM" + UUID.randomUUID().toString().replace("-", ""));
        message.setUserId(userId.value());
        message.setCommandType(InboundMessageEntity.CommandType.ADD);
        message.setBodyCiphertext(new byte[]{1, 2, 3});
        message.setReceivedAt(Instant.now());
        return inboundMessageRepository.save(message);
    }

    private SourceDocumentEntity source(
            UserId userId,
            String displayName,
            String language,
            SourceDocumentEntity.Status status,
            UUID inboundMessageId
    ) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setUserId(userId.value());
        source.setInboundMessageId(inboundMessageId);
        source.setKind(SourceDocumentEntity.Kind.PASTED_TEXT);
        source.setDisplayName(displayName);
        source.setStatus(status);
        source.setDetectedMediaType("text/plain; charset=UTF-8");
        source.setLanguage(language);
        source.setMetadata(new LinkedHashMap<>());
        return sourceDocumentRepository.save(source);
    }

    private void passage(UserId userId, SourceDocumentEntity source, int ordinal, String text) {
        PassageEntity passage = new PassageEntity();
        passage.setUserId(userId.value());
        passage.setSourceId(source.getId());
        passage.setOrdinal(ordinal);
        passage.setDisplayText(text);
        passage.setNormalizedText(text);
        passage.setLocation(new LinkedHashMap<>());
        passage.setTokenCount(text.split(" ").length);
        passageRepository.save(passage);
    }

    private CardEntity card(UserId userId, SourceDocumentEntity source) {
        DeckEntity deck = new DeckEntity();
        deck.setUserId(userId.value());
        deck.setName("Default");
        deck.setNormalizedName("default-" + UUID.randomUUID());
        deck.setStatus(DeckEntity.Status.ACTIVE);
        deck = deckRepository.save(deck);

        CardEntity card = new CardEntity();
        card.setUserId(userId.value());
        card.setDeckId(deck.getId());
        card.setNoteType(CardEntity.NoteType.BASIC_CONTEXT);
        card.setSourceLanguage("es");
        card.setDefinitionLanguage("en");
        card.setTerm("eliminable");
        card.setDefinition("removable");
        card.setExampleText("texto eliminable");
        card.setSourceId(source.getId());
        card.setSourceLocation(new LinkedHashMap<>());
        card.setPayload(new LinkedHashMap<>());
        return cardRepository.save(card);
    }
}
