package com.example.whatsanki.service.document;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.ExtractDocumentJobPayloadDto;
import com.example.whatsanki.service.document.DocumentExtractionService;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.exception.DocumentExtractionException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExtractDocumentBackgroundJobHandlerTest {

    private final DocumentExtractionService extractionService = mock(DocumentExtractionService.class);
    private final ExtractDocumentBackgroundJobHandler handler =
            new ExtractDocumentBackgroundJobHandler(extractionService);

    @Test
    void extractsValidPdfJob() {
        SourceId sourceId = SourceId.random();
        ClaimedBackgroundJobDto job = job(sourceId, 1, 3);

        assertThatCode(() -> handler.handle(job)).doesNotThrowAnyException();
        verify(extractionService).extractAndIndex(new UserId(job.userId()), sourceId);
    }

    @Test
    void permanentlyFailsTextlessPdfAndClosesSource() {
        SourceId sourceId = SourceId.random();
        ClaimedBackgroundJobDto job = job(sourceId, 1, 3);
        doThrow(DocumentExtractionException.permanent("PDF_NEEDS_OCR"))
                .when(extractionService).extractAndIndex(new UserId(job.userId()), sourceId);

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.PERMANENT);
                    assertThat(exception.errorCode()).isEqualTo("PDF_NEEDS_OCR");
                });
        verify(extractionService).markFailed(new UserId(job.userId()), sourceId, "PDF_NEEDS_OCR");
    }

    private ClaimedBackgroundJobDto job(SourceId sourceId, int attempts, int maxAttempts) {
        return new ClaimedBackgroundJobDto(
                JobId.random(), UUID.randomUUID(), BackgroundJobEntity.Type.EXTRACT_DOCUMENT,
                ExtractDocumentJobPayloadDto.create(sourceId).toMap(), attempts, maxAttempts
        );
    }
}
