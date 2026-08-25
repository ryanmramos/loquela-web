package com.example.whatsanki.service.source;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.SourceUploadEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.AcceptedSourceDto;
import com.example.whatsanki.dto.CreateSourceUploadRequestDto;
import com.example.whatsanki.dto.DetectedDocumentTypeDto;
import com.example.whatsanki.dto.ExtractDocumentJobPayloadDto;
import com.example.whatsanki.dto.SourceUploadIntentDto;
import com.example.whatsanki.dto.StoredObjectMetadataDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.repository.SourceUploadRepository;
import com.example.whatsanki.service.BrowserSourceUploadService;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.exception.BrowserSourceConflictException;
import com.example.whatsanki.service.exception.BrowserSourceNotFoundException;
import com.example.whatsanki.service.exception.BrowserSourceValidationException;
import com.example.whatsanki.service.exception.MediaAcquisitionException;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.service.storage.ObjectStore;
import com.example.whatsanki.service.util.DocumentMediaTypeDetector;
import com.example.whatsanki.service.util.ObjectKeyFactory;
import com.example.whatsanki.service.util.SourceReferenceCodec;
import com.example.whatsanki.service.util.TextNormalizer;
import com.example.whatsanki.service.util.UploadReferenceCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Validates browser uploads, stores the original object, and queues extraction. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrowserSourceUploadServiceImpl implements BrowserSourceUploadService {
    private static final List<String> ACCEPTED_MEDIA_TYPES =
            List.of("application/pdf", "text/plain", "application/epub+zip");

    private final UserIdentityService userIdentityService;
    private final AppUserRepository appUserRepository;
    private final SourceUploadRepository sourceUploadRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final DocumentMediaTypeDetector mediaTypeDetector;
    private final ObjectStore objectStore;
    private final ObjectKeyFactory objectKeyFactory;
    private final BackgroundJobService backgroundJobService;
    private final TextNormalizer textNormalizer;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    @Transactional
    public SourceUploadIntentDto createIntent(String googleSubject, CreateSourceUploadRequestDto request) {
        UserId userId = resolveUser(googleSubject);
        long maximumBytes = configuration.getIngestion().getMaxDownloadBytes();
        if (request.size() > maximumBytes) {
            throw new BrowserSourceValidationException("DOCUMENT_TOO_LARGE", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String mediaType = normalizeMediaType(request.mediaType());
        if (!ACCEPTED_MEDIA_TYPES.contains(mediaType)) {
            throw new BrowserSourceValidationException("UNSUPPORTED_MEDIA_TYPE", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        String displayName = textNormalizer.normalizeDisplay(request.fileName());
        if (displayName.isBlank()) {
            throw new BrowserSourceValidationException("INVALID_FILE_NAME", HttpStatus.BAD_REQUEST);
        }

        SourceUploadEntity upload = new SourceUploadEntity();
        upload.setId(UUID.randomUUID());
        upload.setUserId(userId.value());
        upload.setDisplayName(displayName);
        upload.setDeclaredMediaType(mediaType);
        upload.setDeclaredSize(request.size());
        upload.setDeclaredSha256(HexFormat.of().parseHex(request.sha256()));
        upload.setState(SourceUploadEntity.State.PENDING);
        upload.setExpiresAt(Instant.now().plus(configuration.getIngestion().getUploadIntentLifetime()));
        sourceUploadRepository.save(upload);

        String reference = UploadReferenceCodec.encode(upload.getId());
        return new SourceUploadIntentDto(
                reference,
                SourceUploadIntentDto.UploadMode.APPLICATION,
                "/api/v1/source-uploads/" + reference + "/complete",
                maximumBytes,
                ACCEPTED_MEDIA_TYPES,
                upload.getExpiresAt()
        );
    }

    @Override
    @Transactional
    public AcceptedSourceDto complete(String googleSubject, String uploadReference, MultipartFile file) {
        UserId userId = resolveUser(googleSubject);
        UUID uploadId = UploadReferenceCodec.decode(uploadReference)
                .orElseThrow(BrowserSourceNotFoundException::new);
        SourceUploadEntity upload = sourceUploadRepository.findForUpdateByIdAndUserId(uploadId, userId.value())
                .orElseThrow(BrowserSourceNotFoundException::new);
        if (upload.getState() == SourceUploadEntity.State.COMPLETED) {
            throw new BrowserSourceConflictException("UPLOAD_ALREADY_COMPLETED");
        }
        if (upload.getState() != SourceUploadEntity.State.PENDING || !upload.getExpiresAt().isAfter(Instant.now())) {
            throw new BrowserSourceConflictException("UPLOAD_EXPIRED");
        }
        validateFile(upload, file);

        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("loquela-source-", ".upload");
            file.transferTo(temporaryFile);
            byte[] checksum = sha256(temporaryFile);
            if (!MessageDigest.isEqual(checksum, upload.getDeclaredSha256())) {
                throw new BrowserSourceValidationException("CHECKSUM_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            DetectedDocumentTypeDto detected =
                    mediaTypeDetector.detect(temporaryFile, upload.getDeclaredMediaType());
            SourceId sourceId = SourceId.random();
            String objectKey = objectKeyFactory.originalSource(userId, sourceId);
            StoredObjectMetadataDto metadata =
                    new StoredObjectMetadataDto(upload.getDeclaredSize(), checksum, detected.mediaType());
            // Store the original before publishing the source row. If persistence fails,
            // the deterministic key lets a later cleanup scan remove the orphan.
            objectStore.put(objectKey, temporaryFile, metadata);

            AppUserEntity user = appUserRepository.findById(userId.value())
                    .filter(candidate -> candidate.getStatus() == AppUserEntity.Status.ACTIVE)
                    .orElseThrow(() -> new IllegalStateException("Active user was not found for source upload"));
            SourceDocumentEntity source = new SourceDocumentEntity();
            source.setId(sourceId.value());
            source.setUserId(userId.value());
            source.setKind(detected.sourceKind());
            source.setDisplayName(upload.getDisplayName());
            source.setStatus(SourceDocumentEntity.Status.STORED);
            source.setDetectedMediaType(detected.mediaType());
            source.setObjectKey(objectKey);
            source.setOriginalStoredAt(Instant.now());
            source.setSha256(checksum);
            source.setLanguage(new LanguageTag(user.getSourceLanguage()).value());
            source.setMetadata(new LinkedHashMap<>(Map.of(
                    "origin", "WEBSITE_UPLOAD",
                    "mediaState", "STORED",
                    "contentLength", upload.getDeclaredSize()
            )));
            sourceDocumentRepository.save(source);
            backgroundJobService.enqueue(
                    userId,
                    BackgroundJobEntity.Type.EXTRACT_DOCUMENT,
                    ExtractDocumentJobPayloadDto.create(sourceId).toMap()
            );

            upload.setState(SourceUploadEntity.State.COMPLETED);
            upload.setSourceId(sourceId.value());
            upload.setCompletedAt(Instant.now());
            sourceUploadRepository.save(upload);
            log.info(
                    "Completed browser source upload userId={} sourceId={} kind={} byteCount={}",
                    userId.value(), sourceId.value(), detected.sourceKind(), upload.getDeclaredSize()
            );
            return new AcceptedSourceDto(
                    SourceReferenceCodec.encode(sourceId),
                    SourceDocumentEntity.Status.STORED,
                    false
            );
        } catch (BrowserSourceValidationException exception) {
            throw exception;
        } catch (MediaAcquisitionException exception) {
            throw new BrowserSourceValidationException(
                    exception.errorCode(),
                    exception.errorCode().equals("DOCUMENT_TOO_LARGE")
                            ? HttpStatus.PAYLOAD_TOO_LARGE
                            : HttpStatus.UNPROCESSABLE_ENTITY
            );
        } catch (IOException exception) {
            throw new BrowserSourceValidationException("UPLOAD_READ_FAILED", HttpStatus.UNPROCESSABLE_ENTITY);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException exception) {
                    log.warn("Could not delete temporary browser upload file");
                }
            }
        }
    }

    private void validateFile(SourceUploadEntity upload, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BrowserSourceValidationException("EMPTY_DOCUMENT", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() != upload.getDeclaredSize()) {
            throw new BrowserSourceValidationException("UPLOAD_SIZE_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (file.getSize() > configuration.getIngestion().getMaxDownloadBytes()) {
            throw new BrowserSourceValidationException("DOCUMENT_TOO_LARGE", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String suppliedType = normalizeMediaType(file.getContentType());
        if (!suppliedType.equals(upload.getDeclaredMediaType())) {
            throw new BrowserSourceValidationException("MEDIA_TYPE_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private byte[] sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizeMediaType(String mediaType) {
        if (mediaType == null) {
            return "";
        }
        return mediaType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
    }

    private UserId resolveUser(String googleSubject) {
        return userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, googleSubject)
                .orElseThrow(() -> new IllegalStateException("Authenticated Google identity is not linked"));
    }
}
