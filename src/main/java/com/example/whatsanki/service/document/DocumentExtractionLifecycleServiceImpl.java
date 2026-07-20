package com.example.whatsanki.service.document;

import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.PassageEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DocumentExtractionPreparationDto;
import com.example.whatsanki.dto.PassageDraftDto;
import com.example.whatsanki.repository.PassageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.exception.DocumentExtractionException;
import com.example.whatsanki.service.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentExtractionLifecycleServiceImpl implements DocumentExtractionLifecycleService {

    private final SourceDocumentRepository sourceDocumentRepository;
    private final PassageRepository passageRepository;
    private final TextNormalizer textNormalizer;

    @Override
    @Transactional
    public DocumentExtractionPreparationDto prepare(UserId userId, SourceId sourceId) {
        SourceDocumentEntity source = findSource(userId, sourceId);
        if (source.getStatus() == SourceDocumentEntity.Status.READY) {
            return DocumentExtractionPreparationDto.ready();
        }
        if (source.getKind() != SourceDocumentEntity.Kind.PDF) {
            throw DocumentExtractionException.permanent("INVALID_SOURCE_KIND");
        }
        if (source.getStatus() != SourceDocumentEntity.Status.STORED
                && source.getStatus() != SourceDocumentEntity.Status.EXTRACTING
                && source.getStatus() != SourceDocumentEntity.Status.INDEXING) {
            throw DocumentExtractionException.permanent("INVALID_SOURCE_STATE");
        }
        if (source.getObjectKey() == null || source.getObjectKey().isBlank()) {
            throw DocumentExtractionException.permanent("SOURCE_OBJECT_NOT_FOUND");
        }

        source.setStatus(SourceDocumentEntity.Status.EXTRACTING);
        source.setErrorCode(null);
        source.setErrorDetailSafe(null);
        sourceDocumentRepository.save(source);
        return new DocumentExtractionPreparationDto(
                false,
                source.getObjectKey(),
                source.getKind(),
                new LanguageTag(source.getLanguage())
        );
    }

    @Override
    @Transactional
    public void complete(
            UserId userId,
            SourceId sourceId,
            List<PassageDraftDto> drafts,
            long characterCount
    ) {
        Objects.requireNonNull(drafts, "drafts must not be null");
        if (drafts.isEmpty() || characterCount < 1) {
            throw DocumentExtractionException.permanent("PDF_NEEDS_OCR");
        }

        SourceDocumentEntity source = findSource(userId, sourceId);
        if (source.getStatus() == SourceDocumentEntity.Status.READY) {
            log.info("Skipped already-ready PDF indexing userId={} sourceId={}", userId.value(), sourceId.value());
            return;
        }
        if (source.getKind() != SourceDocumentEntity.Kind.PDF
                || (source.getStatus() != SourceDocumentEntity.Status.EXTRACTING
                && source.getStatus() != SourceDocumentEntity.Status.INDEXING)) {
            throw DocumentExtractionException.permanent("INVALID_SOURCE_STATE");
        }

        source.setStatus(SourceDocumentEntity.Status.INDEXING);
        sourceDocumentRepository.save(source);

        // A lease can expire after parsing but before completion. Replacing passages in
        // the same transaction makes a replay deterministic and prevents duplicate rows.
        passageRepository.deleteAllByUserIdAndSourceId(userId.value(), sourceId.value());
        List<PassageEntity> passages = new ArrayList<>(drafts.size());
        for (int ordinal = 0; ordinal < drafts.size(); ordinal++) {
            PassageDraftDto draft = drafts.get(ordinal);
            PassageEntity passage = new PassageEntity();
            passage.setId(UUID.randomUUID());
            passage.setUserId(userId.value());
            passage.setSourceId(sourceId.value());
            passage.setOrdinal(ordinal);
            passage.setDisplayText(draft.displayText());
            passage.setNormalizedText(textNormalizer.normalizeForSearch(draft.displayText()));
            passage.setLocation(new LinkedHashMap<>(draft.location()));
            passage.setTokenCount(draft.tokenCount());
            passages.add(passage);
        }
        passageRepository.saveAll(passages);

        source.setCharacterCount(characterCount);
        source.setPassageCount(passages.size());
        source.setErrorCode(null);
        source.setErrorDetailSafe(null);
        source.setStatus(SourceDocumentEntity.Status.READY);
        sourceDocumentRepository.save(source);
        log.info(
                "Completed PDF indexing userId={} sourceId={} passageCount={} characterCount={}",
                userId.value(), sourceId.value(), passages.size(), characterCount
        );
    }

    @Override
    @Transactional
    public void markFailed(UserId userId, SourceId sourceId, String errorCode) {
        DocumentExtractionException validated = DocumentExtractionException.permanent(errorCode);
        var result = sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value());
        if (result.isEmpty()) {
            return;
        }
        SourceDocumentEntity source = result.orElseThrow();
        if (source.getStatus() == SourceDocumentEntity.Status.READY
                || source.getStatus() == SourceDocumentEntity.Status.DELETING) {
            log.warn("Refused to overwrite source during extraction failure userId={} sourceId={} status={}",
                    userId.value(), sourceId.value(), source.getStatus());
            return;
        }
        if (source.getStatus() == SourceDocumentEntity.Status.FAILED) {
            return;
        }
        source.setStatus(SourceDocumentEntity.Status.FAILED);
        source.setErrorCode(validated.errorCode());
        source.setErrorDetailSafe(null);
        sourceDocumentRepository.save(source);
        log.warn("Marked PDF extraction failed userId={} sourceId={} errorCode={}",
                userId.value(), sourceId.value(), errorCode);
    }

    private SourceDocumentEntity findSource(UserId userId, SourceId sourceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        return sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value())
                .orElseThrow(() -> DocumentExtractionException.permanent("SOURCE_NOT_FOUND"));
    }
}
