package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.SourceUploadEntity;
import com.example.whatsanki.domain.TimedTranscriptMetadataEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.AcceptedSourceDto;
import com.example.whatsanki.dto.CreateTimedTranscriptUploadRequestDto;
import com.example.whatsanki.dto.SourceUploadIntentDto;
import com.example.whatsanki.dto.StoredObjectMetadataDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.repository.SourceUploadRepository;
import com.example.whatsanki.repository.TimedTranscriptMetadataRepository;
import com.example.whatsanki.service.TimedTranscriptUploadService;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.exception.BrowserSourceConflictException;
import com.example.whatsanki.service.exception.BrowserSourceNotFoundException;
import com.example.whatsanki.service.exception.BrowserSourceValidationException;
import com.example.whatsanki.service.exception.TimedTranscriptException;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.service.storage.ObjectStore;
import com.example.whatsanki.service.util.ObjectKeyFactory;
import com.example.whatsanki.service.util.SourceReferenceCodec;
import com.example.whatsanki.service.util.TextNormalizer;
import com.example.whatsanki.service.util.UploadReferenceCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Validates transcript uploads, stores their original bytes, and queues parsing. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimedTranscriptUploadServiceImpl implements TimedTranscriptUploadService {
    private static final Pattern LANGUAGE_SUFFIX = Pattern.compile(
            "(?i)[a-z]{2,3}(?:[-_][a-z0-9]{2,8})?"
    );
    private static final List<String> ACCEPTED_TYPES = List.of(
            "text/vtt", "application/x-subrip", "text/srt", "text/plain", "application/octet-stream"
    );
    private final UserIdentityService userIdentityService;
    private final SourceUploadRepository uploadRepository;
    private final SourceDocumentRepository sourceRepository;
    private final TimedTranscriptMetadataRepository metadataRepository;
    private final ObjectStore objectStore;
    private final ObjectKeyFactory objectKeyFactory;
    private final BackgroundJobService jobService;
    private final TextNormalizer textNormalizer;
    private final OriginUrlPolicy originUrlPolicy;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    @Transactional
    public SourceUploadIntentDto createIntent(String googleSubject, CreateTimedTranscriptUploadRequestDto request) {
        requireEnabled();
        UserId userId = user(googleSubject);
        if (request.size() > configuration.getTimedTranscript().getMaxFileBytes()) {
            throw validation("TIMED_TRANSCRIPT_TOO_LARGE", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String filename = safeFilename(request.fileName());
        TimedTranscriptFormat format = format(filename);
        String mediaType = mediaType(request.mediaType());
        if (!ACCEPTED_TYPES.contains(mediaType)) {
            throw validation("TIMED_TRANSCRIPT_FORMAT_UNSUPPORTED", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        String language;
        try { language = new LanguageTag(request.language()).value(); }
        catch (IllegalArgumentException exception) { throw validation("INVALID_SOURCE_LANGUAGE", HttpStatus.BAD_REQUEST); }
        OriginUrlPolicy.Origin origin = validateOrigin(request.originUrl());

        SourceUploadEntity upload = new SourceUploadEntity();
        upload.setId(UUID.randomUUID());
        upload.setUserId(userId.value());
        upload.setDisplayName(filename);
        upload.setDeclaredMediaType(mediaType);
        upload.setDeclaredSize(request.size());
        upload.setDeclaredSha256(HexFormat.of().parseHex(request.sha256()));
        upload.setPurpose(SourceUploadEntity.Purpose.TIMED_TRANSCRIPT);
        upload.setTranscriptFormat(format.name());
        upload.setLanguage(language);
        upload.setOriginUrl(origin.canonicalUrl());
        upload.setTimelineOffsetMs(request.timelineOffsetMs());
        upload.setState(SourceUploadEntity.State.PENDING);
        upload.setExpiresAt(Instant.now().plus(configuration.getIngestion().getUploadIntentLifetime()));
        uploadRepository.save(upload);
        String reference = UploadReferenceCodec.encode(upload.getId());
        return new SourceUploadIntentDto(reference, SourceUploadIntentDto.UploadMode.APPLICATION,
                "/api/v1/timed-transcript-uploads/" + reference + "/complete",
                configuration.getTimedTranscript().getMaxFileBytes(), ACCEPTED_TYPES, upload.getExpiresAt());
    }

    @Override
    @Transactional
    public AcceptedSourceDto complete(String googleSubject, String uploadReference, MultipartFile file) {
        requireEnabled();
        UserId userId = user(googleSubject);
        UUID uploadId = UploadReferenceCodec.decode(uploadReference).orElseThrow(BrowserSourceNotFoundException::new);
        SourceUploadEntity upload = uploadRepository.findForUpdateByIdAndUserId(uploadId, userId.value())
                .filter(candidate -> candidate.getPurpose() == SourceUploadEntity.Purpose.TIMED_TRANSCRIPT)
                .orElseThrow(BrowserSourceNotFoundException::new);
        if (upload.getState() == SourceUploadEntity.State.COMPLETED) {
            throw new BrowserSourceConflictException("UPLOAD_ALREADY_COMPLETED");
        }
        if (upload.getState() != SourceUploadEntity.State.PENDING || !upload.getExpiresAt().isAfter(Instant.now())) {
            throw new BrowserSourceConflictException("UPLOAD_EXPIRED");
        }
        OriginUrlPolicy.Origin origin = validateOrigin(upload.getOriginUrl());
        validateFile(upload, file);
        Path temporary = null;
        String objectKey = null;
        try {
            temporary = Files.createTempFile("loquela-timed-transcript-", ".upload");
            file.transferTo(temporary);
            byte[] checksum = sha256(temporary);
            if (!MessageDigest.isEqual(checksum, upload.getDeclaredSha256())) {
                throw validation("CHECKSUM_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            SourceId sourceId = SourceId.random();
            objectKey = objectKeyFactory.originalSource(userId, sourceId);
            objectStore.put(objectKey, temporary,
                    new StoredObjectMetadataDto(upload.getDeclaredSize(), checksum, upload.getDeclaredMediaType()));
            // Register rollback cleanup because object storage is outside the database transaction.
            registerRollbackCleanup(objectKey);

            SourceDocumentEntity source = new SourceDocumentEntity();
            source.setId(sourceId.value());
            source.setUserId(userId.value());
            source.setKind(SourceDocumentEntity.Kind.TIMED_TRANSCRIPT);
            source.setDisplayName(defaultDisplayName(upload.getDisplayName()));
            source.setStatus(SourceDocumentEntity.Status.STORED);
            source.setDetectedMediaType(upload.getDeclaredMediaType());
            source.setObjectKey(objectKey);
            source.setOriginalStoredAt(Instant.now());
            source.setSha256(checksum);
            source.setLanguage(upload.getLanguage());
            source.setMetadata(Map.of("origin", "WEBSITE_UPLOAD", "activityStage", "STORED"));
            sourceRepository.save(source);

            TimedTranscriptMetadataEntity metadata = new TimedTranscriptMetadataEntity();
            metadata.setSourceId(sourceId.value());
            metadata.setUserId(userId.value());
            metadata.setOriginalFilename(upload.getDisplayName());
            metadata.setFormat(TimedTranscriptMetadataEntity.Format.valueOf(upload.getTranscriptFormat()));
            metadata.setLanguage(upload.getLanguage());
            metadata.setOriginUrl(origin.canonicalUrl());
            metadata.setOriginProvider(origin.provider() == null ? null
                    : TimedTranscriptMetadataEntity.OriginProvider.valueOf(origin.provider()));
            metadata.setOriginProviderId(origin.providerId());
            metadata.setTimelineOffsetMs(upload.getTimelineOffsetMs());
            metadata.setParserVersion("1");
            metadata.setNormalizerVersion(RollingCaptionNormalizer.VERSION);
            metadata.setTemporaryObjectKey(objectKey);
            metadataRepository.save(metadata);

            jobService.enqueue(userId, BackgroundJobEntity.Type.INGEST_TIMED_TRANSCRIPT,
                    Map.of("schemaVersion", 1, "sourceId", sourceId.value().toString()));
            upload.setState(SourceUploadEntity.State.COMPLETED);
            upload.setSourceId(sourceId.value());
            upload.setCompletedAt(Instant.now());
            uploadRepository.save(upload);
            return new AcceptedSourceDto(SourceReferenceCodec.encode(sourceId), source.getStatus(), false);
        } catch (IOException exception) {
            cleanupObject(objectKey);
            throw validation("TIMED_TRANSCRIPT_PROCESSING_FAILED", HttpStatus.UNPROCESSABLE_ENTITY);
        } catch (RuntimeException exception) {
            if (!(exception instanceof BrowserSourceValidationException)) cleanupObject(objectKey);
            throw exception;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); }
            catch (IOException exception) { log.warn("Could not delete temporary timed transcript upload file"); }
        }
    }

    private void validateFile(SourceUploadEntity upload, MultipartFile file) {
        if (file == null || file.isEmpty()) throw validation("TIMED_TRANSCRIPT_EMPTY", HttpStatus.BAD_REQUEST);
        if (file.getSize() != upload.getDeclaredSize()) throw validation("UPLOAD_SIZE_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY);
        if (file.getSize() > configuration.getTimedTranscript().getMaxFileBytes()) {
            throw validation("TIMED_TRANSCRIPT_TOO_LARGE", HttpStatus.PAYLOAD_TOO_LARGE);
        }
    }

    private String safeFilename(String raw) {
        String value = textNormalizer.normalizeDisplay(raw);
        if (value.isBlank() || value.contains("/") || value.contains("\\") || value.equals(".") || value.equals("..")
                || value.chars().anyMatch(Character::isISOControl)) {
            throw validation("INVALID_FILE_NAME", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String defaultDisplayName(String filename) {
        String stem = filename.replaceFirst("(?i)\\.(?:vtt|srt)$", "");
        int separator = stem.lastIndexOf('.');
        if (separator > 0 && LANGUAGE_SUFFIX.matcher(stem.substring(separator + 1)).matches()) {
            stem = stem.substring(0, separator);
        }
        return stem.isBlank() ? filename : stem;
    }

    private TimedTranscriptFormat format(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".vtt")) return TimedTranscriptFormat.VTT;
        if (lower.endsWith(".srt")) return TimedTranscriptFormat.SRT;
        throw validation("TIMED_TRANSCRIPT_FORMAT_UNSUPPORTED", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    private String mediaType(String raw) { return raw.split(";", 2)[0].strip().toLowerCase(Locale.ROOT); }
    private byte[] sha256(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) != -1;) digest.update(buffer, 0, read);
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
    private void cleanupObject(String objectKey) { if (objectKey != null) try { objectStore.delete(objectKey); } catch (RuntimeException ignored) { } }
    private void registerRollbackCleanup(String objectKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) cleanupObject(objectKey);
            }
        });
    }
    private OriginUrlPolicy.Origin validateOrigin(String raw) {
        try {
            return originUrlPolicy.validate(raw);
        } catch (TimedTranscriptException exception) {
            throw validation(exception.errorCode(), HttpStatus.BAD_REQUEST);
        }
    }
    private void requireEnabled() { if (!configuration.getTimedTranscript().isEnabled()) throw validation("TIMED_TRANSCRIPT_DISABLED", HttpStatus.NOT_FOUND); }
    private BrowserSourceValidationException validation(String code, HttpStatus status) { return new BrowserSourceValidationException(code, status); }
    private UserId user(String subject) { return userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, subject)
            .orElseThrow(() -> new IllegalStateException("Authenticated Google identity is not linked")); }
}
