package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.ObjectStore;
import com.example.whatsanki.service.TwilioMediaCleanupScheduler;
import com.example.whatsanki.service.util.ObjectKeyFactory;
import com.example.whatsanki.service.exception.SourceDeletionException;
import com.example.whatsanki.service.exception.ObjectStoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceDeletionServiceImplTest {

    @Mock private SourceDocumentRepository sourceDocumentRepository;
    @Mock private BackgroundJobRepository backgroundJobRepository;
    @Mock private InboundMessageRepository inboundMessageRepository;
    @Mock private ObjectStore objectStore;
    @Mock private TwilioMediaCleanupScheduler twilioMediaCleanupScheduler;

    private SourceDeletionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SourceDeletionServiceImpl(
                sourceDocumentRepository,
                backgroundJobRepository,
                inboundMessageRepository,
                objectStore,
                new ObjectKeyFactory(),
                twilioMediaCleanupScheduler
        );
    }

    @Test
    void deletesSourceAndEncryptedStagingAfterRelatedWorkIsQuiescent() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        JobId jobId = JobId.random();
        UUID inboundMessageId = UUID.randomUUID();
        SourceDocumentEntity source = source(sourceId, inboundMessageId);
        InboundMessageEntity inbound = new InboundMessageEntity();
        inbound.setBodyCiphertext(new byte[]{1});
        inbound.setMediaUrlCiphertext(new byte[]{2});
        inbound.setMediaContentType("text/plain");
        when(sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value()))
                .thenReturn(Optional.of(source));
        when(backgroundJobRepository.countRunningForSource(
                userId.value(), sourceId.value().toString(), jobId.value()
        )).thenReturn(0L);
        when(inboundMessageRepository.findByIdAndUserId(inboundMessageId, userId.value()))
                .thenReturn(Optional.of(inbound));

        assertThat(service.delete(userId, sourceId, jobId)).isTrue();

        verify(backgroundJobRepository).cancelPendingForSource(
                eq(userId.value()), eq(sourceId.value().toString()), eq(jobId.value()), any(Instant.class)
        );
        verify(sourceDocumentRepository).delete(source);
        assertThat(inbound.getBodyCiphertext()).isNull();
        assertThat(inbound.getMediaUrlCiphertext()).isNull();
        assertThat(inbound.getMediaContentType()).isNull();
    }

    @Test
    void retriesWhileAnotherSourceJobIsRunning() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        JobId jobId = JobId.random();
        SourceDocumentEntity source = source(sourceId, null);
        when(sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value()))
                .thenReturn(Optional.of(source));
        when(backgroundJobRepository.countRunningForSource(
                userId.value(), sourceId.value().toString(), jobId.value()
        )).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(userId, sourceId, jobId))
                .isInstanceOfSatisfying(SourceDeletionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.TRANSIENT);
                    assertThat(exception.errorCode()).isEqualTo("SOURCE_BUSY");
                });
        verify(sourceDocumentRepository, never()).delete(any());
    }

    @Test
    void treatsAnAlreadyDeletedSourceAsSuccessfulIdempotency() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        when(sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value()))
                .thenReturn(Optional.empty());

        assertThat(service.delete(userId, sourceId, JobId.random())).isFalse();
        verify(backgroundJobRepository, never()).cancelPendingForSource(any(), any(), any(), any());
    }

    @Test
    void deletesStoredObjectBeforeDeletingSourceRow() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        SourceDocumentEntity source = source(sourceId, null);
        source.setObjectKey("users/internal/source/original");
        when(sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value()))
                .thenReturn(Optional.of(source));

        when(backgroundJobRepository.countRunningForSource(
                eq(userId.value()), eq(sourceId.value().toString()), any()
        )).thenReturn(0L);

        assertThat(service.delete(userId, sourceId, JobId.random())).isTrue();

        verify(objectStore).delete("users/internal/source/original");
        verify(sourceDocumentRepository).delete(source);
    }

    @Test
    void retriesWithoutDeletingDatabaseRowWhenObjectStoreIsUnavailable() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        JobId jobId = JobId.random();
        SourceDocumentEntity source = source(sourceId, null);
        source.setObjectKey("users/internal/source/original");
        when(sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value()))
                .thenReturn(Optional.of(source));
        when(backgroundJobRepository.countRunningForSource(
                userId.value(), sourceId.value().toString(), jobId.value()
        )).thenReturn(0L);
        doThrow(new ObjectStoreException("unavailable", new RuntimeException()))
                .when(objectStore).delete(source.getObjectKey());

        assertThatThrownBy(() -> service.delete(userId, sourceId, jobId))
                .isInstanceOfSatisfying(SourceDeletionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.TRANSIENT);
                    assertThat(exception.errorCode()).isEqualTo("SOURCE_OBJECT_DELETE_FAILED");
                });
        verify(sourceDocumentRepository, never()).delete(any());
    }

    private SourceDocumentEntity source(SourceId sourceId, UUID inboundMessageId) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(sourceId.value());
        source.setStatus(SourceDocumentEntity.Status.DELETING);
        source.setKind(SourceDocumentEntity.Kind.PASTED_TEXT);
        source.setInboundMessageId(inboundMessageId);
        return source;
    }
}
