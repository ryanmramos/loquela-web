package com.example.whatsanki.service.document;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DetectedDocumentTypeDto;
import com.example.whatsanki.dto.StoredObjectMetadataDto;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.SensitiveDataEncryptionService;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.service.storage.SourceObjectCleanupScheduler;
import com.example.whatsanki.service.twilio.TwilioMediaCleanupScheduler;
import com.example.whatsanki.service.util.TwilioMediaUrlPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentMediaLifecycleServiceImplTest {

    @Mock private SourceDocumentRepository sourceRepository;
    @Mock private InboundMessageRepository inboundRepository;
    @Mock private SensitiveDataEncryptionService encryptionService;
    @Mock private TwilioMediaUrlPolicy urlPolicy;
    @Mock private BackgroundJobService backgroundJobService;
    @Mock private TwilioMediaCleanupScheduler twilioMediaCleanupScheduler;
    @Mock private SourceObjectCleanupScheduler sourceObjectCleanupScheduler;

    private DocumentMediaLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentMediaLifecycleServiceImpl(
                sourceRepository, inboundRepository, encryptionService, urlPolicy,
                backgroundJobService, twilioMediaCleanupScheduler, sourceObjectCleanupScheduler
        );
    }

    @Test
    void preparesEncryptedReferenceAndMovesSourceToFetching() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        InboundMessageId inboundId = InboundMessageId.random();
        SourceDocumentEntity source = source(sourceId, inboundId, SourceDocumentEntity.Status.RECEIVED);
        InboundMessageEntity inbound = inbound(inboundId, userId);
        inbound.setMediaUrlCiphertext(new byte[]{1});
        inbound.setMediaContentType("application/pdf");
        inbound.setTwilioMediaSid("ME22222222222222222222222222222222");
        when(sourceRepository.findByIdAndUserId(sourceId.value(), userId.value()))
                .thenReturn(Optional.of(source));
        when(inboundRepository.findByIdAndUserId(inboundId.value(), userId.value()))
                .thenReturn(Optional.of(inbound));
        when(encryptionService.decrypt(any(), any())).thenReturn("https://api.twilio.com/media");

        var prepared = service.prepare(userId, sourceId);

        assertThat(prepared.mediaUri()).isEqualTo(URI.create("https://api.twilio.com/media"));
        assertThat(source.getStatus()).isEqualTo(SourceDocumentEntity.Status.FETCHING);
        verify(urlPolicy).validate(prepared.mediaUri());
    }

    @Test
    void recordsStoredObjectAndClearsEncryptedMediaStaging() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        InboundMessageId inboundId = InboundMessageId.random();
        SourceDocumentEntity source = source(sourceId, inboundId, SourceDocumentEntity.Status.FETCHING);
        InboundMessageEntity inbound = inbound(inboundId, userId);
        inbound.setMediaUrlCiphertext(new byte[]{1});
        inbound.setMediaContentType("application/pdf");
        inbound.setTwilioMediaSid("ME22222222222222222222222222222222");
        when(sourceRepository.findByIdAndUserId(sourceId.value(), userId.value()))
                .thenReturn(Optional.of(source));
        when(inboundRepository.findByIdAndUserId(inboundId.value(), userId.value()))
                .thenReturn(Optional.of(inbound));

        var outcome = service.complete(
                userId, sourceId, "users/u/sources/s/original",
                new DetectedDocumentTypeDto(SourceDocumentEntity.Kind.PDF, "application/pdf"),
                new StoredObjectMetadataDto(12, new byte[32], "application/pdf")
        );

        assertThat(outcome.name()).isEqualTo("STORED");
        assertThat(source.getStatus()).isEqualTo(SourceDocumentEntity.Status.STORED);
        assertThat(source.getObjectKey()).isEqualTo("users/u/sources/s/original");
        assertThat(source.getOriginalStoredAt()).isNotNull();
        assertThat(source.getMetadata()).containsEntry("contentLength", 12L);
        assertThat(inbound.getMediaUrlCiphertext()).isNull();
        assertThat(inbound.getMediaContentType()).isNull();
        verify(inboundRepository).save(inbound);
        verify(backgroundJobService).enqueue(
                eq(userId),
                eq(BackgroundJobEntity.Type.EXTRACT_DOCUMENT),
                any()
        );
        verify(twilioMediaCleanupScheduler).enqueueIfNeeded(userId, inboundId);
    }

    @Test
    void failedAcquisitionIsIdempotentAndQueuesDurableCleanup() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        InboundMessageId inboundId = InboundMessageId.random();
        SourceDocumentEntity source = source(sourceId, inboundId, SourceDocumentEntity.Status.FETCHING);
        InboundMessageEntity inbound = inbound(inboundId, userId);
        inbound.setMediaUrlCiphertext(new byte[]{1});
        inbound.setMediaContentType("application/pdf");
        inbound.setTwilioMediaSid("ME22222222222222222222222222222222");
        when(sourceRepository.findByIdAndUserId(sourceId.value(), userId.value()))
                .thenReturn(Optional.of(source));
        when(inboundRepository.findByIdAndUserId(inboundId.value(), userId.value()))
                .thenReturn(Optional.of(inbound));

        assertThat(service.markFailed(userId, sourceId, "INVALID_PDF", true)).isTrue();
        assertThat(service.markFailed(userId, sourceId, "MEDIA_DOWNLOAD_FAILED", true)).isTrue();

        assertThat(source.getStatus()).isEqualTo(SourceDocumentEntity.Status.FAILED);
        assertThat(source.getErrorCode()).isEqualTo("INVALID_PDF");
        assertThat(inbound.getMediaUrlCiphertext()).isNull();
        verify(sourceObjectCleanupScheduler, org.mockito.Mockito.times(2))
                .enqueueIfNeeded(userId, sourceId);
        verify(twilioMediaCleanupScheduler, org.mockito.Mockito.times(2))
                .enqueueIfNeeded(userId, inboundId);
    }

    private SourceDocumentEntity source(
            SourceId sourceId,
            InboundMessageId inboundId,
            SourceDocumentEntity.Status status
    ) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(sourceId.value());
        source.setInboundMessageId(inboundId.value());
        source.setKind(SourceDocumentEntity.Kind.PDF);
        source.setStatus(status);
        source.setMetadata(new LinkedHashMap<>());
        return source;
    }

    private InboundMessageEntity inbound(InboundMessageId inboundId, UserId userId) {
        InboundMessageEntity inbound = new InboundMessageEntity();
        inbound.setId(inboundId.value());
        inbound.setUserId(userId.value());
        return inbound;
    }
}
