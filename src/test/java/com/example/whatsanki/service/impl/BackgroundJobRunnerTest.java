package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.BackgroundJobDispatcher;
import com.example.whatsanki.service.BackgroundJobExecutionException;
import com.example.whatsanki.service.BackgroundJobService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackgroundJobRunnerTest {

    @Mock
    private BackgroundJobService backgroundJobService;

    @Mock
    private BackgroundJobDispatcher backgroundJobDispatcher;

    private BackgroundJobRunner runner;

    @BeforeEach
    void setUp() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.getJobs().setLeaseDuration(Duration.ofMinutes(5));
        configuration.getJobs().setBatchSize(5);
        runner = new BackgroundJobRunner(backgroundJobService, backgroundJobDispatcher, configuration);
    }

    @Test
    void completesSuccessfullyDispatchedJob() {
        ClaimedBackgroundJobDto job = job();
        when(backgroundJobService.claimBatch(anyString(), any(Instant.class), eq(Duration.ofMinutes(5)), eq(5)))
                .thenReturn(List.of(job));

        runner.poll();

        verify(backgroundJobService).recoverExpiredLeases(any(Instant.class));
        verify(backgroundJobDispatcher).dispatch(job);
        verify(backgroundJobService).complete(eq(job.jobId()), anyString(), any(Instant.class));
    }

    @Test
    void recordsClassifiedHandlerFailure() {
        ClaimedBackgroundJobDto job = job();
        when(backgroundJobService.claimBatch(anyString(), any(Instant.class), any(Duration.class), anyInt()))
                .thenReturn(List.of(job));
        doThrow(new BackgroundJobExecutionException(
                BackgroundJobFailureKind.PERMANENT,
                "INVALID_JOB_PAYLOAD",
                null
        )).when(backgroundJobDispatcher).dispatch(job);

        runner.poll();

        verify(backgroundJobService).fail(
                eq(job),
                anyString(),
                eq(BackgroundJobFailureKind.PERMANENT),
                eq("INVALID_JOB_PAYLOAD"),
                any(Instant.class)
        );
    }

    @Test
    void treatsUnexpectedRuntimeExceptionAsTransientSafeFailure() {
        ClaimedBackgroundJobDto job = job();
        when(backgroundJobService.claimBatch(anyString(), any(Instant.class), any(Duration.class), anyInt()))
                .thenReturn(List.of(job));
        doThrow(new IllegalStateException("potentially sensitive downstream detail"))
                .when(backgroundJobDispatcher).dispatch(job);

        runner.poll();

        verify(backgroundJobService).fail(
                eq(job),
                anyString(),
                eq(BackgroundJobFailureKind.TRANSIENT),
                eq("UNEXPECTED_JOB_ERROR"),
                any(Instant.class)
        );
    }

    private ClaimedBackgroundJobDto job() {
        return new ClaimedBackgroundJobDto(
                JobId.random(),
                UUID.randomUUID(),
                BackgroundJobEntity.Type.INGEST_TEXT,
                Map.of("schemaVersion", 1, "sourceId", UUID.randomUUID().toString()),
                1,
                3
        );
    }
}
