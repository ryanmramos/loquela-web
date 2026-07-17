package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.BackgroundJobHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackgroundJobDispatcherImplTest {

    @Test
    void routesJobToRegisteredTypeHandler() {
        BackgroundJobHandler handler = mock(BackgroundJobHandler.class);
        when(handler.supportedType()).thenReturn(BackgroundJobEntity.Type.INGEST_TEXT);
        BackgroundJobDispatcherImpl dispatcher = new BackgroundJobDispatcherImpl(List.of(handler));
        ClaimedBackgroundJobDto job = job(BackgroundJobEntity.Type.INGEST_TEXT);

        dispatcher.dispatch(job);

        verify(handler).handle(job);
    }

    @Test
    void classifiesMissingHandlerAsPermanentSafeFailure() {
        BackgroundJobDispatcherImpl dispatcher = new BackgroundJobDispatcherImpl(List.of());

        assertThatThrownBy(() -> dispatcher.dispatch(job(BackgroundJobEntity.Type.LOOKUP)))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.PERMANENT);
                    assertThat(exception.errorCode()).isEqualTo("NO_JOB_HANDLER");
                });
    }

    @Test
    void rejectsDuplicateHandlerRegistration() {
        BackgroundJobHandler first = mock(BackgroundJobHandler.class);
        BackgroundJobHandler second = mock(BackgroundJobHandler.class);
        when(first.supportedType()).thenReturn(BackgroundJobEntity.Type.LOOKUP);
        when(second.supportedType()).thenReturn(BackgroundJobEntity.Type.LOOKUP);

        assertThatThrownBy(() -> new BackgroundJobDispatcherImpl(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LOOKUP");
    }

    private ClaimedBackgroundJobDto job(BackgroundJobEntity.Type type) {
        return new ClaimedBackgroundJobDto(
                JobId.random(),
                UUID.randomUUID(),
                type,
                Map.of("schemaVersion", 1, "sourceId", UUID.randomUUID().toString()),
                1,
                3
        );
    }
}
