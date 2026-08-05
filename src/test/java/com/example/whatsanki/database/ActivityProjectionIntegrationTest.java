package com.example.whatsanki.database;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.DeckEntity;
import com.example.whatsanki.domain.ExportRequestEntity;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.LookupSessionEntity;
import com.example.whatsanki.domain.OutboundMessageEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.repository.ActivityProjectionRepository;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.repository.ExportRequestRepository;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.LookupSessionRepository;
import com.example.whatsanki.repository.OutboundMessageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityProjectionIntegrationTest extends PostgreSqlIntegrationTest {
    private final AppUserRepository appUserRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final LookupSessionRepository lookupSessionRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final OutboundMessageRepository outboundMessageRepository;
    private final DeckRepository deckRepository;
    private final ExportRequestRepository exportRequestRepository;
    private final ActivityProjectionRepository activityProjectionRepository;
    private final EntityManager entityManager;

    ActivityProjectionIntegrationTest(
            AppUserRepository appUserRepository,
            SourceDocumentRepository sourceDocumentRepository,
            LookupSessionRepository lookupSessionRepository,
            InboundMessageRepository inboundMessageRepository,
            OutboundMessageRepository outboundMessageRepository,
            DeckRepository deckRepository,
            ExportRequestRepository exportRequestRepository,
            ActivityProjectionRepository activityProjectionRepository,
            EntityManager entityManager
    ) {
        this.appUserRepository = appUserRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.lookupSessionRepository = lookupSessionRepository;
        this.inboundMessageRepository = inboundMessageRepository;
        this.outboundMessageRepository = outboundMessageRepository;
        this.deckRepository = deckRepository;
        this.exportRequestRepository = exportRequestRepository;
        this.activityProjectionRepository = activityProjectionRepository;
        this.entityManager = entityManager;
    }

    @Test
    @Transactional
    void failedWhatsappDeliveryFailsTheLogicalLookupProjection() {
        Instant time = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(30);
        AppUserEntity owner = appUserRepository.saveAndFlush(user());
        InboundMessageEntity inbound = new InboundMessageEntity();
        inbound.setUserId(owner.getId());
        inbound.setTwilioMessageSid("SM" + java.util.UUID.randomUUID().toString().replace("-", ""));
        inbound.setCommandType(InboundMessageEntity.CommandType.LOOKUP);
        inbound.setReceivedAt(time);
        inbound = inboundMessageRepository.save(inbound);
        entityManager.flush();
        LookupSessionEntity lookup = lookup(owner, time);
        lookup.setInboundMessageId(inbound.getId());
        lookup = lookupSessionRepository.save(lookup);
        entityManager.flush();
        OutboundMessageEntity outbound = new OutboundMessageEntity();
        outbound.setUserId(owner.getId());
        outbound.setLookupSessionId(lookup.getId());
        outbound.setPartNumber(1);
        outbound.setBody("safe fixture");
        outbound.setStatus(OutboundMessageEntity.Status.FAILED);
        outbound.setErrorCode("PERMANENT_DELIVERY_FAILURE");
        outbound.setCreatedAt(time.plusSeconds(1));
        outbound.setUpdatedAt(time.plusSeconds(1));
        outboundMessageRepository.save(outbound);
        entityManager.flush();

        List<ActivityProjectionRepository.ActivityRow> rows = activityProjectionRepository.findPage(
                owner.getId(), "LOOKUP", null, null, null, null, 20
        );

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getLifecycle()).isEqualTo("FAILED");
            assertThat(row.getDeliveryStatus()).isEqualTo("FAILED");
        });
        assertThat(activityProjectionRepository.countActive(owner.getId())).isZero();
    }

    @Test
    @Transactional
    void mergesLogicalResourcesWithStableCursorAndOwnerIsolation() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(60);
        AppUserEntity owner = appUserRepository.saveAndFlush(user());
        AppUserEntity other = appUserRepository.saveAndFlush(user());
        sourceDocumentRepository.save(source(owner, base.plusSeconds(1)));
        sourceDocumentRepository.save(source(other, base.plusSeconds(10)));
        lookupSessionRepository.save(lookup(owner, base.plusSeconds(2)));
        DeckEntity deck = deckRepository.saveAndFlush(deck(owner, base));
        exportRequestRepository.save(export(owner, deck, base.plusSeconds(3)));
        entityManager.flush();

        List<ActivityProjectionRepository.ActivityRow> firstPage = activityProjectionRepository.findPage(
                owner.getId(), null, null, null, null, null, 2
        );

        assertThat(firstPage).extracting(ActivityProjectionRepository.ActivityRow::getCategory)
                .containsExactly("EXPORT", "LOOKUP");
        assertThat(activityProjectionRepository.countActive(owner.getId())).isEqualTo(2);

        var cursorRow = firstPage.get(1);
        List<ActivityProjectionRepository.ActivityRow> secondPage = activityProjectionRepository.findPage(
                owner.getId(), null, null, cursorRow.getUpdatedAt(), cursorRow.getCategory(), cursorRow.getId(), 2
        );

        assertThat(secondPage).singleElement().satisfies(row -> {
            assertThat(row.getCategory()).isEqualTo("SOURCE");
            assertThat(row.getLifecycle()).isEqualTo("COMPLETED");
            assertThat(row.getResourceLabel()).isEqualTo("Owner notes");
        });
    }

    private AppUserEntity user() {
        AppUserEntity user = new AppUserEntity();
        user.setSourceLanguage("es");
        user.setDefinitionLanguage("en");
        user.setStatus(AppUserEntity.Status.ACTIVE);
        return user;
    }

    private SourceDocumentEntity source(AppUserEntity user, Instant time) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setUserId(user.getId());
        source.setKind(SourceDocumentEntity.Kind.PASTED_TEXT);
        source.setDisplayName("Owner notes");
        source.setStatus(SourceDocumentEntity.Status.READY);
        source.setLanguage("es");
        source.setCharacterCount(20L);
        source.setPassageCount(1);
        source.setCreatedAt(time);
        source.setUpdatedAt(time);
        return source;
    }

    private LookupSessionEntity lookup(AppUserEntity user, Instant time) {
        LookupSessionEntity lookup = new LookupSessionEntity();
        lookup.setUserId(user.getId());
        lookup.setRequestedTerm("hablar");
        lookup.setNormalizedTerm("hablar");
        lookup.setSourceLanguage("es");
        lookup.setDefinitionLanguage("en");
        lookup.setResultPayload(new LinkedHashMap<>());
        lookup.setRequestFilters(new LinkedHashMap<>());
        lookup.setStatus(LookupSessionEntity.Status.PENDING);
        lookup.setCreatedAt(time);
        lookup.setUpdatedAt(time);
        lookup.setExpiresAt(time.plusSeconds(3600));
        return lookup;
    }

    private DeckEntity deck(AppUserEntity user, Instant time) {
        DeckEntity deck = new DeckEntity();
        deck.setUserId(user.getId());
        deck.setName("Spanish conversation");
        deck.setNormalizedName("spanish conversation");
        deck.setStatus(DeckEntity.Status.ACTIVE);
        deck.setCreatedAt(time);
        deck.setUpdatedAt(time);
        return deck;
    }

    private ExportRequestEntity export(AppUserEntity user, DeckEntity deck, Instant time) {
        ExportRequestEntity export = new ExportRequestEntity();
        export.setUserId(user.getId());
        export.setDeckId(deck.getId());
        export.setState(ExportRequestEntity.State.QUEUED);
        export.setSnapshotPayload(new LinkedHashMap<>());
        export.setSnapshotSha256(new byte[]{1, 2, 3});
        export.setTemplateVersion("v1");
        export.setCreatedAt(time);
        export.setUpdatedAt(time);
        return export;
    }
}
