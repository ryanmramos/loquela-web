package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.domain.PassageEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.TimedTranscriptMetadataEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.PassageDraftDto;
import com.example.whatsanki.dto.TimedCaptionCueDto;
import com.example.whatsanki.repository.PassageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.repository.TimedTranscriptMetadataRepository;
import com.example.whatsanki.service.util.TextNormalizer;
import com.example.whatsanki.service.exception.TimedTranscriptException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimedTranscriptLifecycleServiceImpl implements TimedTranscriptLifecycleService {
    private static final String PARSER_VERSION = "1";
    private final SourceDocumentRepository sourceRepository;
    private final TimedTranscriptMetadataRepository metadataRepository;
    private final PassageRepository passageRepository;
    private final TextNormalizer textNormalizer;

    @Override
    @Transactional
    public Preparation prepare(UserId userId, SourceId sourceId) {
        SourceDocumentEntity source = source(userId, sourceId);
        TimedTranscriptMetadataEntity metadata = metadata(userId, sourceId);
        if (source.getStatus() == SourceDocumentEntity.Status.READY) {
            return preparation(true, metadata);
        }
        if (source.getKind() != SourceDocumentEntity.Kind.TIMED_TRANSCRIPT
                || !List.of(SourceDocumentEntity.Status.STORED, SourceDocumentEntity.Status.PARSING,
                    SourceDocumentEntity.Status.NORMALIZING, SourceDocumentEntity.Status.INDEXING)
                    .contains(source.getStatus())
                || metadata.getTemporaryObjectKey() == null) {
            throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_PROCESSING_FAILED");
        }
        stage(source, SourceDocumentEntity.Status.PARSING, "PARSING");
        return preparation(false, metadata);
    }

    @Override
    @Transactional
    public void storeParsedMetadata(UserId userId, SourceId sourceId, TimedTranscriptParseResult result) {
        SourceDocumentEntity source = source(userId, sourceId);
        TimedTranscriptMetadataEntity metadata = metadata(userId, sourceId);
        metadata.setCueCount(result.cues().size());
        metadata.setParserVersion(PARSER_VERSION);
        metadata.setIgnoredStructuralBlockCount(result.ignoredStructuralBlockCount());
        metadata.setStrippedMarkupCount(result.strippedMarkupCount());
        metadata.setEmptyCueCount(result.emptyCueCount());
        metadataRepository.save(metadata);
        stage(source, SourceDocumentEntity.Status.NORMALIZING, "NORMALIZING");
    }

    @Override
    @Transactional
    public void markIndexing(UserId userId, SourceId sourceId, int coalescedCueCount) {
        SourceDocumentEntity source = source(userId, sourceId);
        TimedTranscriptMetadataEntity metadata = metadata(userId, sourceId);
        metadata.setCoalescedCueCount(coalescedCueCount);
        metadata.setNormalizerVersion(RollingCaptionNormalizer.VERSION);
        metadataRepository.save(metadata);
        stage(source, SourceDocumentEntity.Status.INDEXING, "INDEXING");
    }

    @Override
    @Transactional
    public void complete(UserId userId, SourceId sourceId, List<PassageDraftDto> drafts,
                         long normalizedCharacters, long timelineEndMs) {
        SourceDocumentEntity source = source(userId, sourceId);
        TimedTranscriptMetadataEntity metadata = metadata(userId, sourceId);
        if (source.getStatus() == SourceDocumentEntity.Status.READY) return;
        if (source.getStatus() != SourceDocumentEntity.Status.INDEXING || drafts.isEmpty()) {
            throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_PROCESSING_FAILED");
        }
        passageRepository.deleteAllByUserIdAndSourceId(userId.value(), sourceId.value());
        List<PassageEntity> passages = new ArrayList<>(drafts.size());
        for (int ordinal = 0; ordinal < drafts.size(); ordinal++) {
            PassageDraftDto draft = drafts.get(ordinal);
            PassageEntity passage = new PassageEntity();
            passage.setId(UUID.randomUUID()); passage.setUserId(userId.value()); passage.setSourceId(sourceId.value());
            passage.setOrdinal(ordinal); passage.setDisplayText(draft.displayText());
            passage.setNormalizedText(textNormalizer.normalizeForSearch(draft.displayText()));
            passage.setLocation(new LinkedHashMap<>(draft.location())); passage.setTokenCount(draft.tokenCount());
            passages.add(passage);
        }
        passageRepository.saveAll(passages);
        metadata.setNormalizedCharacterCount(normalizedCharacters);
        metadata.setTimelineEndMs(timelineEndMs);
        metadataRepository.save(metadata);
        source.setCharacterCount(normalizedCharacters);
        source.setPassageCount(passages.size());
        source.setObjectKey(null);
        stage(source, SourceDocumentEntity.Status.READY, "READY");
    }

    @Override
    @Transactional
    public void markFailed(UserId userId, SourceId sourceId, String errorCode) {
        sourceRepository.findByIdAndUserId(sourceId.value(), userId.value()).ifPresent(source -> {
            if (source.getStatus() == SourceDocumentEntity.Status.READY
                    || source.getStatus() == SourceDocumentEntity.Status.DELETING) return;
            source.setStatus(SourceDocumentEntity.Status.FAILED);
            source.setErrorCode(errorCode);
            source.setErrorDetailSafe(null);
            Map<String, Object> values = new LinkedHashMap<>(source.getMetadata());
            values.put("activityStage", "FAILED"); source.setMetadata(values);
            sourceRepository.save(source);
        });
    }

    @Override
    @Transactional
    public void clearTemporaryObjectKey(UserId userId, SourceId sourceId) {
        metadataRepository.findBySourceIdAndUserId(sourceId.value(), userId.value()).ifPresent(metadata -> {
            metadata.setTemporaryObjectKey(null); metadataRepository.save(metadata);
        });
    }

    private void stage(SourceDocumentEntity source, SourceDocumentEntity.Status status, String activityStage) {
        source.setStatus(status); source.setErrorCode(null); source.setErrorDetailSafe(null);
        Map<String, Object> values = new LinkedHashMap<>(source.getMetadata());
        values.put("activityStage", activityStage); source.setMetadata(values); sourceRepository.save(source);
    }
    private Preparation preparation(boolean ready, TimedTranscriptMetadataEntity metadata) {
        return new Preparation(ready, metadata.getTemporaryObjectKey(),
                TimedTranscriptFormat.valueOf(metadata.getFormat().name()),
                new OriginUrlPolicy.Origin(metadata.getOriginUrl(),
                        metadata.getOriginProvider() == null ? null : metadata.getOriginProvider().name(),
                        metadata.getOriginProviderId()), metadata.getTimelineOffsetMs());
    }
    private SourceDocumentEntity source(UserId userId, SourceId sourceId) {
        return sourceRepository.findByIdAndUserId(sourceId.value(), userId.value())
                .orElseThrow(() -> TimedTranscriptException.permanent("SOURCE_NOT_FOUND"));
    }
    private TimedTranscriptMetadataEntity metadata(UserId userId, SourceId sourceId) {
        return metadataRepository.findBySourceIdAndUserId(sourceId.value(), userId.value())
                .orElseThrow(() -> TimedTranscriptException.permanent("TIMED_TRANSCRIPT_PROCESSING_FAILED"));
    }
}
