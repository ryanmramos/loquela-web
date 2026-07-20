package com.example.whatsanki.service.source;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.SourceDeletionRequestDto;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.service.util.SourceReferenceCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceManagementServiceImplTest {

    @Mock private SourceDocumentRepository sourceDocumentRepository;
    @Mock private BackgroundJobRepository backgroundJobRepository;
    @Mock private BackgroundJobService backgroundJobService;

    private SourceManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SourceManagementServiceImpl(
                sourceDocumentRepository,
                backgroundJobRepository,
                backgroundJobService
        );
    }

    @Test
    void listsOnlyTheRequestedUsersRecentSourcesAsExternalReferences() {
        UserId userId = UserId.random();
        SourceDocumentEntity source = source(SourceId.random(), SourceDocumentEntity.Status.READY);
        when(sourceDocumentRepository.findAllByUserIdOrderByCreatedAtDesc(
                userId.value(), PageRequest.of(0, 10)
        )).thenReturn(List.of(source));

        var results = service.listRecent(userId, 10);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.sourceId().value()).isEqualTo(source.getId());
            assertThat(SourceReferenceCodec.decode(result.reference()))
                    .contains(new SourceId(source.getId()));
            assertThat(result.status()).isEqualTo(SourceDocumentEntity.Status.READY);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void queuesDeletionTransactionallyAndCancelsOlderPendingSourceWork() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        SourceDocumentEntity source = source(sourceId, SourceDocumentEntity.Status.READY);
        JobId jobId = JobId.random();
        when(sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value()))
                .thenReturn(Optional.of(source));
        when(backgroundJobRepository.cancelPendingForSource(
                eq(userId.value()), eq(sourceId.value().toString()), isNull(), any(Instant.class)
        )).thenReturn(2);
        when(backgroundJobService.enqueue(
                eq(userId), eq(BackgroundJobEntity.Type.DELETE_SOURCE), anyMap()
        )).thenReturn(jobId);

        SourceDeletionRequestDto result = service.requestDeletion(
                userId, SourceReferenceCodec.encode(sourceId)
        );

        assertThat(result.outcome()).isEqualTo(SourceDeletionRequestDto.Outcome.QUEUED);
        assertThat(result.jobId()).isEqualTo(jobId);
        assertThat(source.getStatus()).isEqualTo(SourceDocumentEntity.Status.DELETING);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(backgroundJobService).enqueue(
                eq(userId), eq(BackgroundJobEntity.Type.DELETE_SOURCE), payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue())
                .containsEntry("schemaVersion", 1)
                .containsEntry("sourceId", sourceId.value().toString())
                .doesNotContainKeys("text", "objectKey");
    }

    @Test
    void invalidMissingAndAlreadyDeletingSourcesDoNotCreateDuplicateJobs() {
        UserId userId = UserId.random();
        assertThat(service.requestDeletion(userId, "invalid").outcome())
                .isEqualTo(SourceDeletionRequestDto.Outcome.NOT_FOUND_OR_DELETED);

        SourceId missing = SourceId.random();
        when(sourceDocumentRepository.findByIdAndUserId(missing.value(), userId.value()))
                .thenReturn(Optional.empty());
        assertThat(service.requestDeletion(userId, SourceReferenceCodec.encode(missing)).outcome())
                .isEqualTo(SourceDeletionRequestDto.Outcome.NOT_FOUND_OR_DELETED);

        SourceId deletingId = SourceId.random();
        when(sourceDocumentRepository.findByIdAndUserId(deletingId.value(), userId.value()))
                .thenReturn(Optional.of(source(deletingId, SourceDocumentEntity.Status.DELETING)));
        when(backgroundJobRepository.existsActiveForSourceAndType(
                userId.value(),
                deletingId.value().toString(),
                BackgroundJobEntity.Type.DELETE_SOURCE.name()
        )).thenReturn(true);
        assertThat(service.requestDeletion(userId, SourceReferenceCodec.encode(deletingId)).outcome())
                .isEqualTo(SourceDeletionRequestDto.Outcome.ALREADY_PENDING);

        verify(backgroundJobService, never()).enqueue(any(), any(), anyMap());
    }

    @Test
    void replaysADeletingSourceWhenNoActiveDeletionJobRemains() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        JobId replayJobId = JobId.random();
        when(sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value()))
                .thenReturn(Optional.of(source(sourceId, SourceDocumentEntity.Status.DELETING)));
        when(backgroundJobRepository.existsActiveForSourceAndType(
                userId.value(), sourceId.value().toString(), BackgroundJobEntity.Type.DELETE_SOURCE.name()
        )).thenReturn(false);
        when(backgroundJobService.enqueue(
                eq(userId), eq(BackgroundJobEntity.Type.DELETE_SOURCE), anyMap()
        )).thenReturn(replayJobId);

        SourceDeletionRequestDto result = service.requestDeletion(
                userId, SourceReferenceCodec.encode(sourceId)
        );

        assertThat(result.outcome()).isEqualTo(SourceDeletionRequestDto.Outcome.QUEUED);
        assertThat(result.jobId()).isEqualTo(replayJobId);
    }

    private SourceDocumentEntity source(SourceId sourceId, SourceDocumentEntity.Status status) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(sourceId.value());
        source.setKind(SourceDocumentEntity.Kind.PASTED_TEXT);
        source.setStatus(status);
        source.setDisplayName("Pasted text");
        source.setLanguage("es");
        source.setCharacterCount(25L);
        source.setPassageCount(2);
        source.setCreatedAt(Instant.now());
        return source;
    }
}
