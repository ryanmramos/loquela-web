package com.example.whatsanki.service.document;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.PassageEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.IngestTextJobPayloadDto;
import com.example.whatsanki.dto.PassageDraftDto;
import com.example.whatsanki.dto.PastedTextAcceptanceDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.PassageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.service.document.PastedTextIngestionService;
import com.example.whatsanki.service.SensitiveDataEncryptionService;
import com.example.whatsanki.service.exception.PastedTextIngestionException;
import com.example.whatsanki.service.exception.PastedTextLimitExceededException;
import com.example.whatsanki.service.util.PassageSegmenter;
import com.example.whatsanki.service.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PastedTextIngestionServiceImpl implements PastedTextIngestionService {

    private static final String PASTED_TEXT_DISPLAY_NAME = "Pasted text";
    private static final String PASTED_TEXT_MEDIA_TYPE = "text/plain; charset=UTF-8";

    private final AppUserRepository appUserRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final PassageRepository passageRepository;
    private final BackgroundJobService backgroundJobService;
    private final SensitiveDataEncryptionService encryptionService;
    private final TextNormalizer textNormalizer;
    private final PassageSegmenter passageSegmenter;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    @Transactional
    public PastedTextAcceptanceDto accept(UserId userId, InboundMessageId inboundMessageId, String text) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(inboundMessageId, "inboundMessageId must not be null");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Pasted text must not be blank");
        }

        int characterCount = text.codePointCount(0, text.length());
        long maximumCharacters = configuration.getIngestion().getMaxExtractedCharacters();
        if (characterCount > maximumCharacters) {
            throw new PastedTextLimitExceededException(maximumCharacters);
        }

        // The MessageSid boundary normally prevents repeated command execution. This
        // source lookup is a second idempotency guard for direct retries or future callers.
        var existing = sourceDocumentRepository.findByUserIdAndInboundMessageId(
                userId.value(), inboundMessageId.value()
        );
        if (existing.isPresent()) {
            SourceId sourceId = new SourceId(existing.orElseThrow().getId());
            log.info(
                    "Reused existing pasted-text source userId={} inboundMessageId={} sourceId={}",
                    userId.value(), inboundMessageId.value(), sourceId.value()
            );
            return new PastedTextAcceptanceDto(sourceId, characterCount);
        }

        InboundMessageEntity inboundMessage = inboundMessageRepository
                .findByIdAndUserId(inboundMessageId.value(), userId.value())
                .orElseThrow(() -> new IllegalStateException("Inbound message was not found for ADD command"));
        if (inboundMessage.getCommandType() != InboundMessageEntity.CommandType.ADD) {
            throw new IllegalStateException("Inbound message is not an ADD command");
        }

        AppUserEntity user = appUserRepository.findById(userId.value())
                .filter(candidate -> candidate.getStatus() == AppUserEntity.Status.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Active user was not found for ADD command"));
        LanguageTag sourceLanguage = new LanguageTag(user.getSourceLanguage());

        inboundMessage.setBodyCiphertext(encryptionService.encrypt(
                text,
                bodyEncryptionPurpose(userId, inboundMessageId)
        ));
        inboundMessageRepository.save(inboundMessage);

        SourceId sourceId = SourceId.random();
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(sourceId.value());
        source.setUserId(userId.value());
        source.setInboundMessageId(inboundMessageId.value());
        source.setKind(SourceDocumentEntity.Kind.PASTED_TEXT);
        source.setDisplayName(PASTED_TEXT_DISPLAY_NAME);
        source.setStatus(SourceDocumentEntity.Status.RECEIVED);
        source.setDetectedMediaType(PASTED_TEXT_MEDIA_TYPE);
        source.setLanguage(sourceLanguage.value());
        source.setMetadata(new LinkedHashMap<>(Map.of("origin", "WHATSAPP")));
        sourceDocumentRepository.save(source);

        JobId jobId = backgroundJobService.enqueue(
                userId,
                BackgroundJobEntity.Type.INGEST_TEXT,
                IngestTextJobPayloadDto.create(sourceId).toMap()
        );
        log.info(
                "Accepted pasted text userId={} inboundMessageId={} sourceId={} jobId={} characterCount={}",
                userId.value(), inboundMessageId.value(), sourceId.value(), jobId.value(), characterCount
        );
        return new PastedTextAcceptanceDto(sourceId, characterCount);
    }

    @Override
    @Transactional
    public void ingest(UserId userId, SourceId sourceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");

        SourceDocumentEntity source = sourceDocumentRepository
                .findByIdAndUserId(sourceId.value(), userId.value())
                .orElseThrow(() -> new PastedTextIngestionException("SOURCE_NOT_FOUND"));
        if (source.getStatus() == SourceDocumentEntity.Status.READY) {
            log.info("Skipped already-ready pasted-text source userId={} sourceId={}", userId.value(), sourceId.value());
            return;
        }
        validateIngestibleSource(source);

        InboundMessageId inboundMessageId = new InboundMessageId(source.getInboundMessageId());
        InboundMessageEntity inboundMessage = inboundMessageRepository
                .findByIdAndUserId(inboundMessageId.value(), userId.value())
                .orElseThrow(() -> new PastedTextIngestionException("STAGED_TEXT_NOT_FOUND"));
        if (inboundMessage.getBodyCiphertext() == null) {
            throw new PastedTextIngestionException("STAGED_TEXT_NOT_FOUND");
        }

        String stagedText;
        try {
            stagedText = encryptionService.decrypt(
                    inboundMessage.getBodyCiphertext(),
                    bodyEncryptionPurpose(userId, inboundMessageId)
            );
        } catch (RuntimeException exception) {
            throw new PastedTextIngestionException("STAGED_TEXT_UNREADABLE", exception);
        }

        source.setStatus(SourceDocumentEntity.Status.EXTRACTING);
        String displayText = textNormalizer.normalizeDisplay(stagedText);
        if (displayText.isBlank()) {
            throw new PastedTextIngestionException("EMPTY_TEXT");
        }

        source.setStatus(SourceDocumentEntity.Status.INDEXING);
        List<PassageDraftDto> drafts = passageSegmenter.segment(
                displayText,
                new LanguageTag(source.getLanguage())
        );
        if (drafts.isEmpty()) {
            throw new PastedTextIngestionException("EMPTY_TEXT");
        }

        // Passage writes and the READY transition share one transaction. Search can
        // therefore never observe a partially indexed source as ready.
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

        source.setCharacterCount((long) displayText.codePointCount(0, displayText.length()));
        source.setPassageCount(passages.size());
        source.setErrorCode(null);
        source.setErrorDetailSafe(null);
        source.setStatus(SourceDocumentEntity.Status.READY);
        sourceDocumentRepository.save(source);

        // READY passages are the durable copy. Clearing staging immediately reduces
        // sensitive retention without waiting for the later scheduled purge feature.
        inboundMessage.setBodyCiphertext(null);
        inboundMessageRepository.save(inboundMessage);
        log.info(
                "Completed pasted-text ingestion userId={} sourceId={} passageCount={} characterCount={}",
                userId.value(), sourceId.value(), passages.size(), source.getCharacterCount()
        );
    }

    @Override
    @Transactional
    public void markFailed(UserId userId, SourceId sourceId, String errorCode) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        PastedTextIngestionException validatedError = new PastedTextIngestionException(errorCode);

        var sourceResult = sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value());
        if (sourceResult.isEmpty()) {
            log.warn("Could not mark missing source as failed userId={} sourceId={} errorCode={}",
                    userId.value(), sourceId.value(), errorCode);
            return;
        }

        SourceDocumentEntity source = sourceResult.orElseThrow();
        if (source.getStatus() == SourceDocumentEntity.Status.READY
                || source.getStatus() == SourceDocumentEntity.Status.DELETING) {
            log.warn("Refused to overwrite terminal source lifecycle userId={} sourceId={} status={} errorCode={}",
                    userId.value(), sourceId.value(), source.getStatus(), errorCode);
            return;
        }
        source.setStatus(SourceDocumentEntity.Status.FAILED);
        source.setErrorCode(validatedError.errorCode());
        source.setErrorDetailSafe(null);
        sourceDocumentRepository.save(source);
        log.warn("Marked pasted-text source failed userId={} sourceId={} errorCode={}",
                userId.value(), sourceId.value(), errorCode);
    }

    private void validateIngestibleSource(SourceDocumentEntity source) {
        if (source.getKind() != SourceDocumentEntity.Kind.PASTED_TEXT) {
            throw new PastedTextIngestionException("INVALID_SOURCE_KIND");
        }
        if (source.getStatus() != SourceDocumentEntity.Status.RECEIVED) {
            throw new PastedTextIngestionException("INVALID_SOURCE_STATE");
        }
        if (source.getInboundMessageId() == null) {
            throw new PastedTextIngestionException("STAGED_TEXT_NOT_FOUND");
        }
    }

    private String bodyEncryptionPurpose(UserId userId, InboundMessageId inboundMessageId) {
        return "inbound-message-body:" + userId.value() + ":" + inboundMessageId.value();
    }
}
