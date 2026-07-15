package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.service.BackgroundJobRetryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackgroundJobServiceImplTest {

    @Mock
    private BackgroundJobRepository backgroundJobRepository;

    @Mock
    private BackgroundJobRetryPolicy retryPolicy;

    private BackgroundJobServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BackgroundJobServiceImpl(
                backgroundJobRepository,
                retryPolicy,
                new WhatsAnkiConfigurationDto()
        );
    }

    @Test
    void enqueuesVersionedIdOnlyPayload() {
        UserId userId = UserId.random();
        UUID sourceId = UUID.randomUUID();
        when(backgroundJobRepository.save(any(BackgroundJobEntity.class))).thenAnswer(invocation -> {
            BackgroundJobEntity job = invocation.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        JobId jobId = service.enqueue(
                userId,
                BackgroundJobEntity.Type.INGEST_TEXT,
                Map.of("schemaVersion", 1, "sourceId", sourceId.toString())
        );

        assertThat(jobId.value()).isNotNull();
        var jobCaptor = org.mockito.ArgumentCaptor.forClass(BackgroundJobEntity.class);
        verify(backgroundJobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getUserId()).isEqualTo(userId.value());
        assertThat(jobCaptor.getValue().getState()).isEqualTo(BackgroundJobEntity.State.QUEUED);
        assertThat(jobCaptor.getValue().getPayload()).containsEntry("sourceId", sourceId.toString());
    }

    @Test
    void rejectsPayloadContentInsteadOfDurableIdReference() {
        assertThatThrownBy(() -> service.enqueue(
                UserId.random(),
                BackgroundJobEntity.Type.INGEST_TEXT,
                Map.of("schemaVersion", 1, "text", "sensitive pasted text")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UUID references");

        verify(backgroundJobRepository, never()).save(any());
    }

    @Test
    void rejectsUnsupportedOrFractionalPayloadSchemaVersion() {
        assertThatThrownBy(() -> service.enqueue(
                UserId.random(),
                BackgroundJobEntity.Type.INGEST_TEXT,
                Map.of("schemaVersion", 1.5, "sourceId", UUID.randomUUID().toString())
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion 1");

        verify(backgroundJobRepository, never()).save(any());
    }

    @Test
    void returnsOnlyRepositoryStatusesForRequestedUser() {
        UserId userId = UserId.random();
        BackgroundJobEntity job = jobEntity(userId.value());
        when(backgroundJobRepository.findRecentByUserId(userId.value(), 10)).thenReturn(List.of(job));

        var statuses = service.findRecent(userId, 10);

        assertThat(statuses).singleElement().satisfies(status -> {
            assertThat(status.id()).isEqualTo(job.getId());
            assertThat(status.type()).isEqualTo(job.getType());
            assertThat(status.state()).isEqualTo(job.getState());
        });
        verify(backgroundJobRepository).findRecentByUserId(userId.value(), 10);
    }

    @Test
    void claimsJobsWithConfiguredLeaseBoundary() {
        Instant now = Instant.parse("2026-07-14T12:00:00Z");
        Duration lease = Duration.ofMinutes(5);
        BackgroundJobEntity job = jobEntity(UUID.randomUUID());
        job.setAttempts(1);
        when(backgroundJobRepository.claimBatch("worker-a", now, now.plus(lease), 5))
                .thenReturn(List.of(job));

        var claimed = service.claimBatch("worker-a", now, lease, 5);

        assertThat(claimed).singleElement().satisfies(result -> {
            assertThat(result.jobId().value()).isEqualTo(job.getId());
            assertThat(result.attempts()).isEqualTo(1);
        });
    }

    @Test
    void transientFailureSchedulesRetryBeforeAttemptLimit() {
        Instant now = Instant.parse("2026-07-14T12:00:00Z");
        ClaimedBackgroundJobDto job = claimedJob(1, 3);
        when(retryPolicy.retryDelay(1)).thenReturn(Duration.ofSeconds(30));
        when(backgroundJobRepository.markRetryWaiting(
                job.jobId().value(), "worker-a", "PROVIDER_TIMEOUT", now.plusSeconds(30), now
        )).thenReturn(1);

        assertThat(service.fail(
                job,
                "worker-a",
                BackgroundJobFailureKind.TRANSIENT,
                "PROVIDER_TIMEOUT",
                now
        )).isTrue();
        verify(backgroundJobRepository, never()).markDead(any(), any(), any(), any());
    }

    @Test
    void permanentOrExhaustedFailureBecomesDead() {
        Instant now = Instant.parse("2026-07-14T12:00:00Z");
        ClaimedBackgroundJobDto permanent = claimedJob(1, 3);
        ClaimedBackgroundJobDto exhausted = claimedJob(3, 3);
        when(backgroundJobRepository.markDead(any(), eq("worker-a"), any(), eq(now))).thenReturn(1);

        assertThat(service.fail(
                permanent, "worker-a", BackgroundJobFailureKind.PERMANENT, "INVALID_PAYLOAD", now
        )).isTrue();
        assertThat(service.fail(
                exhausted, "worker-a", BackgroundJobFailureKind.TRANSIENT, "PROVIDER_TIMEOUT", now
        )).isTrue();
        verify(retryPolicy, never()).retryDelay(anyInt());
    }

    private BackgroundJobEntity jobEntity(UUID userId) {
        BackgroundJobEntity job = new BackgroundJobEntity();
        job.setId(UUID.randomUUID());
        job.setUserId(userId);
        job.setType(BackgroundJobEntity.Type.INGEST_TEXT);
        job.setState(BackgroundJobEntity.State.QUEUED);
        job.setPayload(Map.of("schemaVersion", 1, "sourceId", UUID.randomUUID().toString()));
        job.setAttempts(0);
        job.setMaxAttempts(3);
        job.setRunAfter(Instant.now());
        job.setCreatedAt(Instant.now());
        return job;
    }

    private ClaimedBackgroundJobDto claimedJob(int attempts, int maxAttempts) {
        return new ClaimedBackgroundJobDto(
                JobId.random(),
                UUID.randomUUID(),
                BackgroundJobEntity.Type.LOOKUP,
                Map.of("schemaVersion", 1, "lookupSessionId", UUID.randomUUID().toString()),
                attempts,
                maxAttempts
        );
    }
}
