package com.example.whatsanki.service.document;

import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.dto.ExtractedDocumentDto;
import com.example.whatsanki.dto.ExtractedTextSectionDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.DocumentExtractionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfDocumentTextExtractorImpl implements DocumentTextExtractor {

    private final WhatsAnkiConfigurationDto configuration;
    private final ExecutorService documentExtractionExecutor;

    @Override
    public SourceDocumentEntity.Kind supportedKind() {
        return SourceDocumentEntity.Kind.PDF;
    }

    @Override
    public ExtractedDocumentDto extract(byte[] content) {
        if (content == null || content.length == 0) {
            throw DocumentExtractionException.permanent("INVALID_PDF");
        }

        // Parsing untrusted PDFs runs on a bounded executor. Cancellation is best-effort,
        // so production deployments should additionally isolate workers with process-level
        // CPU and memory limits.
        Future<ExtractedDocumentDto> extraction = documentExtractionExecutor.submit(() -> extractNow(content));
        try {
            return extraction.get(
                    configuration.getIngestion().getExtractionTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (TimeoutException exception) {
            extraction.cancel(true);
            throw DocumentExtractionException.transientFailure("EXTRACTION_TIMEOUT", exception);
        } catch (InterruptedException exception) {
            extraction.cancel(true);
            Thread.currentThread().interrupt();
            throw DocumentExtractionException.transientFailure("EXTRACTION_INTERRUPTED", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof DocumentExtractionException extractionException) {
                throw extractionException;
            }
            throw DocumentExtractionException.transientFailure("PDF_EXTRACTION_FAILED", cause);
        }
    }

    private ExtractedDocumentDto extractNow(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            validateDocument(document);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            List<ExtractedTextSectionDto> sections = new ArrayList<>();
            long characterCount = 0;
            boolean readableTextFound = false;
            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw DocumentExtractionException.transientFailure("EXTRACTION_INTERRUPTED", null);
                }
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String pageText = stripper.getText(document);
                characterCount += pageText.codePointCount(0, pageText.length());
                if (characterCount > configuration.getIngestion().getMaxExtractedCharacters()) {
                    throw DocumentExtractionException.permanent("EXTRACTED_TEXT_TOO_LARGE");
                }
                readableTextFound = readableTextFound || !pageText.isBlank();
                sections.add(new ExtractedTextSectionDto(pageNumber, pageText));
            }
            if (!readableTextFound) {
                throw DocumentExtractionException.permanent("PDF_NEEDS_OCR");
            }
            log.info("Extracted PDF text pageCount={} characterCount={}", sections.size(), characterCount);
            return new ExtractedDocumentDto(sections, characterCount);
        } catch (InvalidPasswordException exception) {
            throw DocumentExtractionException.permanent("ENCRYPTED_DOCUMENT", exception);
        } catch (DocumentExtractionException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw DocumentExtractionException.permanent("INVALID_PDF", exception);
        }
    }

    private void validateDocument(PDDocument document) {
        if (document.isEncrypted()) {
            throw DocumentExtractionException.permanent("ENCRYPTED_DOCUMENT");
        }
        int pageCount = document.getNumberOfPages();
        if (pageCount < 1) {
            throw DocumentExtractionException.permanent("INVALID_PDF");
        }
        if (pageCount > configuration.getIngestion().getMaxPdfPages()) {
            throw DocumentExtractionException.permanent("PDF_TOO_MANY_PAGES");
        }
    }
}
