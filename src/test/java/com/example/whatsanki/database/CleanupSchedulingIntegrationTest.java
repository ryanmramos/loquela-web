package com.example.whatsanki.database;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.BackgroundJobService;
import com.example.whatsanki.service.OriginalDocumentRetentionService;
import com.example.whatsanki.service.TwilioMediaCleanupScheduler;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CleanupSchedulingIntegrationTest extends PostgreSqlIntegrationTest {

    private final AppUserRepository appUserRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final TwilioMediaCleanupScheduler mediaCleanupScheduler;
    private final OriginalDocumentRetentionService retentionService;
    private final BackgroundJobService backgroundJobService;

    CleanupSchedulingIntegrationTest(
            AppUserRepository appUserRepository,
            InboundMessageRepository inboundMessageRepository,
            SourceDocumentRepository sourceDocumentRepository,
            TwilioMediaCleanupScheduler mediaCleanupScheduler,
            OriginalDocumentRetentionService retentionService,
            BackgroundJobService backgroundJobService
    ) {
        this.appUserRepository = appUserRepository;
        this.inboundMessageRepository = inboundMessageRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.mediaCleanupScheduler = mediaCleanupScheduler;
        this.retentionService = retentionService;
        this.backgroundJobService = backgroundJobService;
    }

    @Test
    void pendingProviderMediaIsQueuedOnce() {
        UserId userId = user("provider-cleanup");
        InboundMessageEntity inbound = new InboundMessageEntity();
        inbound.setTwilioMessageSid(sid("MM"));
        inbound.setTwilioMediaSid(sid("ME"));
        inbound.setUserId(userId.value());
        inbound.setCommandType(InboundMessageEntity.CommandType.DOCUMENT);
        inbound.setReceivedAt(Instant.now());
        inbound.setPurgeAfter(Instant.now().plus(1, ChronoUnit.DAYS));
        inboundMessageRepository.save(inbound);

        assertThat(mediaCleanupScheduler.enqueuePending(10)).isEqualTo(1);
        assertThat(mediaCleanupScheduler.enqueuePending(10)).isZero();
        assertThat(backgroundJobService.findRecent(userId, 10))
                .singleElement()
                .extracting(job -> job.type())
                .isEqualTo(BackgroundJobEntity.Type.DELETE_TWILIO_MEDIA);
    }

    @Test
    void expiredOriginalIsQueuedOnceWithoutChangingReadyCorpus() {
        UserId userId = user("object-retention");
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setUserId(userId.value());
        source.setKind(SourceDocumentEntity.Kind.PDF);
        source.setDisplayName("retained.pdf");
        source.setStatus(SourceDocumentEntity.Status.READY);
        source.setDetectedMediaType("application/pdf");
        source.setObjectKey("users/" + userId.value() + "/sources/retained/original");
        source.setOriginalStoredAt(Instant.now().minus(31, ChronoUnit.DAYS));
        source.setLanguage("es");
        source.setCharacterCount(20L);
        source.setPassageCount(1);
        source.setMetadata(new LinkedHashMap<>());
        sourceDocumentRepository.save(source);

        assertThat(retentionService.enqueueExpiredOriginals(Instant.now())).isEqualTo(1);
        assertThat(retentionService.enqueueExpiredOriginals(Instant.now())).isZero();
        assertThat(backgroundJobService.findRecent(userId, 10))
                .singleElement()
                .extracting(job -> job.type())
                .isEqualTo(BackgroundJobEntity.Type.PURGE_SOURCE_OBJECT);
        assertThat(sourceDocumentRepository.findByIdAndUserId(source.getId(), userId.value()))
                .get()
                .extracting(SourceDocumentEntity::getStatus)
                .isEqualTo(SourceDocumentEntity.Status.READY);
    }

    private UserId user(String label) {
        String identity = label + "-" + UUID.randomUUID();
        AppUserEntity user = new AppUserEntity();
        user.setWhatsappAddressHash(identity.getBytes(StandardCharsets.UTF_8));
        user.setWhatsappAddressCiphertext(("encrypted-" + identity).getBytes(StandardCharsets.UTF_8));
        user.setSourceLanguage("es");
        user.setDefinitionLanguage("en");
        user.setStatus(AppUserEntity.Status.ACTIVE);
        return new UserId(appUserRepository.saveAndFlush(user).getId());
    }

    private String sid(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
