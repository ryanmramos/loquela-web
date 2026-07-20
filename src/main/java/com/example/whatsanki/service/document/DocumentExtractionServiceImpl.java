package com.example.whatsanki.service.document;

import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DocumentExtractionPreparationDto;
import com.example.whatsanki.dto.ExtractedDocumentDto;
import com.example.whatsanki.dto.ExtractedTextSectionDto;
import com.example.whatsanki.dto.PassageDraftDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.document.DocumentExtractionLifecycleService;
import com.example.whatsanki.service.document.DocumentExtractionService;
import com.example.whatsanki.service.document.DocumentTextExtractor;
import com.example.whatsanki.service.storage.ObjectStore;
import com.example.whatsanki.service.exception.DocumentExtractionException;
import com.example.whatsanki.service.exception.ObjectStoreException;
import com.example.whatsanki.service.util.PassageSegmenter;
import com.example.whatsanki.service.util.TextNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DocumentExtractionServiceImpl implements DocumentExtractionService {

    private final DocumentExtractionLifecycleService lifecycleService;
    private final ObjectStore objectStore;
    private final PassageSegmenter passageSegmenter;
    private final TextNormalizer textNormalizer;
    private final WhatsAnkiConfigurationDto configuration;
    private final Map<SourceDocumentEntity.Kind, DocumentTextExtractor> extractors;

    public DocumentExtractionServiceImpl(
            DocumentExtractionLifecycleService lifecycleService,
            ObjectStore objectStore,
            PassageSegmenter passageSegmenter,
            TextNormalizer textNormalizer,
            WhatsAnkiConfigurationDto configuration,
            List<DocumentTextExtractor> registeredExtractors
    ) {
        this.lifecycleService = lifecycleService;
        this.objectStore = objectStore;
        this.passageSegmenter = passageSegmenter;
        this.textNormalizer = textNormalizer;
        this.configuration = configuration;
        EnumMap<SourceDocumentEntity.Kind, DocumentTextExtractor> mapped =
                new EnumMap<>(SourceDocumentEntity.Kind.class);
        for (DocumentTextExtractor extractor : registeredExtractors) {
            DocumentTextExtractor duplicate = mapped.put(extractor.supportedKind(), extractor);
            if (duplicate != null) {
                throw new IllegalStateException("Multiple document extractors registered for "
                        + extractor.supportedKind());
            }
        }
        extractors = Map.copyOf(mapped);
        log.info("Registered document text extractors count={}", extractors.size());
    }

    @Override
    public void extractAndIndex(UserId userId, SourceId sourceId) {
        DocumentExtractionPreparationDto preparation = lifecycleService.prepare(userId, sourceId);
        if (preparation.alreadyReady()) {
            log.info("Skipped already-ready document extraction userId={} sourceId={}",
                    userId.value(), sourceId.value());
            return;
        }

        DocumentTextExtractor extractor = extractors.get(preparation.kind());
        if (extractor == null) {
            throw DocumentExtractionException.permanent("NO_DOCUMENT_EXTRACTOR");
        }

        byte[] content = readObject(preparation.objectKey());
        ExtractedDocumentDto extracted = extractor.extract(content);
        List<PassageDraftDto> passages = buildPassages(extracted, preparation.language());
        long characterCount = passages.stream()
                .mapToLong(passage -> passage.displayText().codePointCount(0, passage.displayText().length()))
                .sum();
        if (passages.isEmpty() || characterCount == 0) {
            throw DocumentExtractionException.permanent("PDF_NEEDS_OCR");
        }
        lifecycleService.complete(userId, sourceId, passages, characterCount);
    }

    @Override
    public void markFailed(UserId userId, SourceId sourceId, String errorCode) {
        lifecycleService.markFailed(userId, sourceId, errorCode);
    }

    private byte[] readObject(String objectKey) {
        long maximumBytes = configuration.getIngestion().getMaxDownloadBytes();
        try (InputStream input = objectStore.open(objectKey);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maximumBytes) {
                    throw DocumentExtractionException.permanent("DOCUMENT_TOO_LARGE");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (DocumentExtractionException exception) {
            throw exception;
        } catch (ObjectStoreException | IOException exception) {
            throw DocumentExtractionException.transientFailure("SOURCE_OBJECT_READ_FAILED", exception);
        }
    }

    private List<PassageDraftDto> buildPassages(ExtractedDocumentDto document, LanguageTag language) {
        List<PassageDraftDto> passages = new ArrayList<>();
        for (ExtractedTextSectionDto section : document.sections()) {
            String displayText = textNormalizer.normalizeDisplay(section.text());
            if (displayText.isBlank()) {
                continue;
            }
            for (PassageDraftDto draft : passageSegmenter.segment(displayText, language)) {
                LinkedHashMap<String, Object> location = new LinkedHashMap<>();
                location.putAll(draft.location());
                // The format-owned page coordinate is authoritative if a future
                // segmenter happens to emit a field with the same name.
                location.put("pageNumber", section.pageNumber());
                passages.add(new PassageDraftDto(draft.displayText(), location, draft.tokenCount()));
            }
        }
        return List.copyOf(passages);
    }
}
