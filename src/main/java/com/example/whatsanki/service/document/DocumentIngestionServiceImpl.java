package com.example.whatsanki.service.document;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DetectedDocumentTypeDto;
import com.example.whatsanki.dto.DocumentAcceptanceDto;
import com.example.whatsanki.dto.DownloadedMediaDto;
import com.example.whatsanki.dto.FetchMediaJobPayloadDto;
import com.example.whatsanki.dto.MediaFetchPreparationDto;
import com.example.whatsanki.dto.StoredObjectMetadataDto;
import com.example.whatsanki.dto.TwilioMediaMetadataDto;
import com.example.whatsanki.dto.TwilioMediaReferenceDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.SensitiveDataEncryptionService;
import com.example.whatsanki.service.exception.MediaAcquisitionException;
import com.example.whatsanki.service.exception.ObjectStoreException;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.service.storage.ObjectStore;
import com.example.whatsanki.service.twilio.TwilioMediaDownloader;
import com.example.whatsanki.service.util.DocumentMediaTypeDetector;
import com.example.whatsanki.service.util.ObjectKeyFactory;
import com.example.whatsanki.service.util.TextNormalizer;
import com.example.whatsanki.service.util.TwilioMediaUrlPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionServiceImpl implements DocumentIngestionService {

    private static final String DEFAULT_DISPLAY_NAME = "WhatsApp document";

    private final AppUserRepository appUserRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final BackgroundJobService backgroundJobService;
    private final SensitiveDataEncryptionService encryptionService;
    private final TwilioMediaUrlPolicy mediaUrlPolicy;
    private final TwilioMediaDownloader mediaDownloader;
    private final DocumentMediaTypeDetector mediaTypeDetector;
    private final ObjectStore objectStore;
    private final ObjectKeyFactory objectKeyFactory;
    private final DocumentMediaLifecycleService lifecycleService;
    private final TextNormalizer textNormalizer;

    @Override
    @Transactional
    public DocumentAcceptanceDto accept(
            UserId userId,
            InboundMessageId inboundMessageId,
            TwilioMediaMetadataDto media
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(inboundMessageId, "inboundMessageId must not be null");
        if (media == null || media.count() != 1 || media.url() == null || media.contentType() == null) {
            throw MediaAcquisitionException.permanent("INVALID_MEDIA_MESSAGE");
        }

        URI mediaUri;
        try {
            mediaUri = URI.create(media.url());
        } catch (IllegalArgumentException exception) {
            throw MediaAcquisitionException.permanent("INVALID_MEDIA_URL", exception);
        }
        TwilioMediaReferenceDto mediaReference = mediaUrlPolicy.reference(mediaUri);
        SourceDocumentEntity.Kind declaredKind = declaredKind(media.contentType());

        var existing = sourceDocumentRepository.findByUserIdAndInboundMessageId(
                userId.value(), inboundMessageId.value()
        );
        if (existing.isPresent()) {
            SourceId existingId = new SourceId(existing.orElseThrow().getId());
            log.info("Reused existing document source userId={} inboundMessageId={} sourceId={}",
                    userId.value(), inboundMessageId.value(), existingId.value());
            return new DocumentAcceptanceDto(existingId);
        }

        InboundMessageEntity inbound = inboundMessageRepository
                .findByIdAndUserId(inboundMessageId.value(), userId.value())
                .orElseThrow(() -> new IllegalStateException("Inbound message was not found for document command"));
        if (inbound.getCommandType() != InboundMessageEntity.CommandType.DOCUMENT) {
            throw new IllegalStateException("Inbound message is not a document command");
        }
        if (!mediaReference.messageSid().equalsIgnoreCase(inbound.getTwilioMessageSid())) {
            throw MediaAcquisitionException.permanent("INVALID_MEDIA_URL");
        }
        AppUserEntity user = appUserRepository.findById(userId.value())
                .filter(candidate -> candidate.getStatus() == AppUserEntity.Status.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Active user was not found for document command"));

        inbound.setMediaUrlCiphertext(encryptionService.encrypt(
                mediaUri.toASCIIString(), mediaEncryptionPurpose(userId, inboundMessageId)
        ));
        inbound.setMediaContentType(normalizeMediaType(media.contentType()));
        inbound.setTwilioMediaSid(mediaReference.mediaSid());
        inboundMessageRepository.save(inbound);

        SourceId sourceId = SourceId.random();
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(sourceId.value());
        source.setUserId(userId.value());
        source.setInboundMessageId(inboundMessageId.value());
        source.setKind(declaredKind);
        source.setDisplayName(displayName(media.caption()));
        source.setStatus(SourceDocumentEntity.Status.RECEIVED);
        source.setLanguage(new LanguageTag(user.getSourceLanguage()).value());
        source.setMetadata(new LinkedHashMap<>(Map.of(
                "origin", "WHATSAPP",
                "declaredMediaType", normalizeMediaType(media.contentType())
        )));
        sourceDocumentRepository.save(source);

        JobId jobId = backgroundJobService.enqueue(
                userId,
                BackgroundJobEntity.Type.FETCH_MEDIA,
                FetchMediaJobPayloadDto.create(sourceId).toMap()
        );
        log.info("Accepted document media userId={} inboundMessageId={} sourceId={} jobId={} declaredKind={}",
                userId.value(), inboundMessageId.value(), sourceId.value(), jobId.value(), declaredKind);
        return new DocumentAcceptanceDto(sourceId);
    }

    @Override
    public void fetchAndStore(UserId userId, SourceId sourceId) {
        MediaFetchPreparationDto preparation = lifecycleService.prepare(userId, sourceId);
        if (preparation.alreadyStored()) {
            log.info("Skipped already-stored media userId={} sourceId={}", userId.value(), sourceId.value());
            return;
        }

        String objectKey = objectKeyFactory.originalSource(userId, sourceId);
        try (DownloadedMediaDto downloaded = mediaDownloader.download(preparation.mediaUri())) {
            DetectedDocumentTypeDto detected = mediaTypeDetector.detect(
                    downloaded.path(), preparation.declaredMediaType()
            );
            StoredObjectMetadataDto metadata = new StoredObjectMetadataDto(
                    downloaded.contentLength(), downloaded.sha256(), detected.mediaType()
            );
            objectStore.put(objectKey, downloaded.path(), metadata);
            var outcome = lifecycleService.complete(userId, sourceId, objectKey, detected, metadata);
            if (outcome == DocumentMediaLifecycleService.CompletionOutcome.SOURCE_MISSING) {
                objectStore.delete(objectKey);
            }
            log.info("Completed media acquisition userId={} sourceId={} outcome={} byteCount={} mediaType={}",
                    userId.value(), sourceId.value(), outcome, metadata.contentLength(), metadata.mediaType());
        } catch (MediaAcquisitionException exception) {
            throw exception;
        } catch (ObjectStoreException | IOException exception) {
            throw MediaAcquisitionException.transientFailure("OBJECT_STORE_FAILURE", exception);
        }
    }

    @Override
    public void markFailed(UserId userId, SourceId sourceId, String errorCode, boolean clearStaging) {
        // Durable cleanup is queued by the lifecycle transaction. Acquisition failure
        // can therefore retain its original diagnostic even when object storage is down.
        lifecycleService.markFailed(userId, sourceId, errorCode, clearStaging);
    }

    private SourceDocumentEntity.Kind declaredKind(String mediaType) {
        return switch (normalizeMediaType(mediaType)) {
            case "application/pdf" -> SourceDocumentEntity.Kind.PDF;
            default -> throw MediaAcquisitionException.permanent("UNSUPPORTED_MEDIA_TYPE");
        };
    }

    private String normalizeMediaType(String mediaType) {
        return mediaType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
    }

    private String displayName(String caption) {
        if (caption == null || caption.isBlank()) {
            return DEFAULT_DISPLAY_NAME;
        }
        String normalized = textNormalizer.normalizeDisplay(caption);
        if (normalized.isBlank()) {
            return DEFAULT_DISPLAY_NAME;
        }
        return normalized.length() <= 512 ? normalized : normalized.substring(0, 512);
    }

    private String mediaEncryptionPurpose(UserId userId, InboundMessageId inboundMessageId) {
        return "inbound-message-media-url:" + userId.value() + ":" + inboundMessageId.value();
    }
}
