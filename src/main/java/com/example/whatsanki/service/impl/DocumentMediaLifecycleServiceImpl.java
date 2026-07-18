package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DetectedDocumentTypeDto;
import com.example.whatsanki.dto.MediaFetchPreparationDto;
import com.example.whatsanki.dto.StoredObjectMetadataDto;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.DocumentMediaLifecycleService;
import com.example.whatsanki.service.SensitiveDataEncryptionService;
import com.example.whatsanki.service.exception.MediaAcquisitionException;
import com.example.whatsanki.service.util.TwilioMediaUrlPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentMediaLifecycleServiceImpl implements DocumentMediaLifecycleService {

    private final SourceDocumentRepository sourceDocumentRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final SensitiveDataEncryptionService encryptionService;
    private final TwilioMediaUrlPolicy mediaUrlPolicy;

    @Override
    @Transactional
    public MediaFetchPreparationDto prepare(UserId userId, SourceId sourceId) {
        SourceDocumentEntity source = findSource(userId, sourceId);
        if (source.getStatus() == SourceDocumentEntity.Status.STORED) {
            return MediaFetchPreparationDto.stored();
        }
        if (source.getKind() == SourceDocumentEntity.Kind.PASTED_TEXT
                || (source.getStatus() != SourceDocumentEntity.Status.RECEIVED
                && source.getStatus() != SourceDocumentEntity.Status.FETCHING)) {
            throw MediaAcquisitionException.permanent("INVALID_SOURCE_STATE");
        }
        if (source.getInboundMessageId() == null) {
            throw MediaAcquisitionException.permanent("MEDIA_REFERENCE_NOT_FOUND");
        }

        InboundMessageEntity inbound = inboundMessageRepository
                .findByIdAndUserId(source.getInboundMessageId(), userId.value())
                .orElseThrow(() -> MediaAcquisitionException.permanent("MEDIA_REFERENCE_NOT_FOUND"));
        if (inbound.getMediaUrlCiphertext() == null || inbound.getMediaContentType() == null) {
            throw MediaAcquisitionException.permanent("MEDIA_REFERENCE_NOT_FOUND");
        }

        URI mediaUri;
        try {
            String decrypted = encryptionService.decrypt(
                    inbound.getMediaUrlCiphertext(),
                    mediaEncryptionPurpose(userId, new InboundMessageId(inbound.getId()))
            );
            mediaUri = URI.create(decrypted);
            mediaUrlPolicy.validate(mediaUri);
        } catch (MediaAcquisitionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw MediaAcquisitionException.permanent("MEDIA_REFERENCE_UNREADABLE", exception);
        }

        source.setStatus(SourceDocumentEntity.Status.FETCHING);
        source.setErrorCode(null);
        source.setErrorDetailSafe(null);
        sourceDocumentRepository.save(source);
        return new MediaFetchPreparationDto(false, mediaUri, inbound.getMediaContentType());
    }

    @Override
    @Transactional
    public CompletionOutcome complete(
            UserId userId,
            SourceId sourceId,
            String objectKey,
            DetectedDocumentTypeDto detectedType,
            StoredObjectMetadataDto objectMetadata
    ) {
        var result = sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value());
        if (result.isEmpty()) {
            return CompletionOutcome.SOURCE_MISSING;
        }
        SourceDocumentEntity source = result.orElseThrow();
        if (source.getStatus() != SourceDocumentEntity.Status.FETCHING
                && source.getStatus() != SourceDocumentEntity.Status.STORED
                && source.getStatus() != SourceDocumentEntity.Status.DELETING) {
            throw MediaAcquisitionException.permanent("INVALID_SOURCE_STATE");
        }

        source.setKind(detectedType.sourceKind());
        source.setDetectedMediaType(detectedType.mediaType());
        source.setObjectKey(objectKey);
        source.setSha256(objectMetadata.sha256());
        source.setErrorCode(null);
        source.setErrorDetailSafe(null);
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(source.getMetadata());
        metadata.put("mediaState", "STORED");
        metadata.put("contentLength", objectMetadata.contentLength());
        source.setMetadata(metadata);

        // If deletion won the lifecycle race, retain DELETING but persist the newly
        // created key. The deletion retry can now remove the object without orphaning it.
        CompletionOutcome outcome;
        if (source.getStatus() == SourceDocumentEntity.Status.DELETING) {
            outcome = CompletionOutcome.DELETING;
        } else {
            source.setStatus(SourceDocumentEntity.Status.STORED);
            outcome = CompletionOutcome.STORED;
        }
        sourceDocumentRepository.save(source);
        clearMediaStaging(source, userId);
        return outcome;
    }

    @Override
    @Transactional
    public boolean markFailed(UserId userId, SourceId sourceId, String errorCode, boolean clearStaging) {
        MediaAcquisitionException validated = MediaAcquisitionException.permanent(errorCode);
        var result = sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value());
        if (result.isEmpty()) {
            return false;
        }
        SourceDocumentEntity source = result.orElseThrow();
        if (source.getStatus() == SourceDocumentEntity.Status.STORED
                || source.getStatus() == SourceDocumentEntity.Status.READY
                || source.getStatus() == SourceDocumentEntity.Status.DELETING) {
            log.warn("Refused to overwrite source lifecycle during media failure userId={} sourceId={} status={}",
                    userId.value(), sourceId.value(), source.getStatus());
            return false;
        }
        source.setStatus(SourceDocumentEntity.Status.FAILED);
        source.setErrorCode(validated.errorCode());
        source.setErrorDetailSafe(null);
        sourceDocumentRepository.save(source);
        if (clearStaging) {
            clearMediaStaging(source, userId);
        }
        log.warn("Marked media acquisition failed userId={} sourceId={} errorCode={}",
                userId.value(), sourceId.value(), errorCode);
        return true;
    }

    private SourceDocumentEntity findSource(UserId userId, SourceId sourceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        return sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value())
                .orElseThrow(() -> MediaAcquisitionException.permanent("SOURCE_NOT_FOUND"));
    }

    private void clearMediaStaging(SourceDocumentEntity source, UserId userId) {
        if (source.getInboundMessageId() == null) {
            return;
        }
        inboundMessageRepository.findByIdAndUserId(source.getInboundMessageId(), userId.value())
                .ifPresent(inbound -> {
                    inbound.setMediaUrlCiphertext(null);
                    inbound.setMediaContentType(null);
                    inboundMessageRepository.save(inbound);
                });
    }

    private String mediaEncryptionPurpose(UserId userId, InboundMessageId inboundMessageId) {
        return "inbound-message-media-url:" + userId.value() + ":" + inboundMessageId.value();
    }
}
