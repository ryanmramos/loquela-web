package com.example.whatsanki.service.document;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.DocumentExtractionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.example.whatsanki.support.PdfTestDocumentFactory.encryptedPdf;
import static com.example.whatsanki.support.PdfTestDocumentFactory.textPdf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PdfDocumentTextExtractorImplTest {

    private WhatsAnkiConfigurationDto configuration;
    private ExecutorService executor;
    private PdfDocumentTextExtractorImpl extractor;

    @BeforeEach
    void setUp() {
        configuration = new WhatsAnkiConfigurationDto();
        executor = Executors.newSingleThreadExecutor();
        extractor = new PdfDocumentTextExtractorImpl(configuration, executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void extractsUnicodeTextWithOneBasedPageLocations() {
        var extracted = extractor.extract(textPdf(
                "La primera pagina contiene un arbol.",
                "La segunda pagina contiene contexto."
        ));

        assertThat(extracted.sections()).hasSize(2);
        assertThat(extracted.sections().get(0).pageNumber()).isEqualTo(1);
        assertThat(extracted.sections().get(0).text()).contains("primera pagina");
        assertThat(extracted.sections().get(1).pageNumber()).isEqualTo(2);
        assertThat(extracted.characterCount()).isPositive();
    }

    @Test
    void rejectsScannedOrOtherwiseTextlessPdf() {
        assertThatThrownBy(() -> extractor.extract(textPdf("")))
                .isInstanceOfSatisfying(DocumentExtractionException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("PDF_NEEDS_OCR"));
    }

    @Test
    void rejectsEncryptedPdf() {
        assertThatThrownBy(() -> extractor.extract(encryptedPdf("secret")))
                .isInstanceOfSatisfying(DocumentExtractionException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("ENCRYPTED_DOCUMENT"));
    }

    @Test
    void enforcesPageLimit() {
        configuration.getIngestion().setMaxPdfPages(1);

        assertThatThrownBy(() -> extractor.extract(textPdf("page one", "page two")))
                .isInstanceOfSatisfying(DocumentExtractionException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("PDF_TOO_MANY_PAGES"));
    }

    @Test
    void enforcesExtractedCharacterLimit() {
        configuration.getIngestion().setMaxExtractedCharacters(5);

        assertThatThrownBy(() -> extractor.extract(textPdf("This text is longer than five characters.")))
                .isInstanceOfSatisfying(DocumentExtractionException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("EXTRACTED_TEXT_TOO_LARGE"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelsAndClassifiesTimedOutParserWork() throws Exception {
        ExecutorService stalledExecutor = mock(ExecutorService.class);
        Future<com.example.whatsanki.dto.ExtractedDocumentDto> future = mock(Future.class);
        when(stalledExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        when(future.get(anyLong(), eq(TimeUnit.MILLISECONDS))).thenThrow(new TimeoutException());
        PdfDocumentTextExtractorImpl timedExtractor = new PdfDocumentTextExtractorImpl(
                configuration,
                stalledExecutor
        );

        assertThatThrownBy(() -> timedExtractor.extract(textPdf("Readable text.")))
                .isInstanceOfSatisfying(DocumentExtractionException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("EXTRACTION_TIMEOUT"));
        verify(future).cancel(true);
    }
}
