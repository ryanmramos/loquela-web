package com.example.whatsanki.service.source;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.domain.VideoSourceEntity;
import com.example.whatsanki.dto.AcceptedSourceDto;
import com.example.whatsanki.dto.BrowserSourceDetailsDto;
import com.example.whatsanki.dto.BrowserSourceSummaryDto;
import com.example.whatsanki.dto.ExtractDocumentJobPayloadDto;
import com.example.whatsanki.dto.FetchVideoSubtitlesJobPayloadDto;
import com.example.whatsanki.dto.IngestTextJobPayloadDto;
import com.example.whatsanki.dto.PastedTextAcceptanceDto;
import com.example.whatsanki.dto.PastedTextSourceDto;
import com.example.whatsanki.dto.SourceDeletionRequestDto;
import com.example.whatsanki.dto.SourceListDto;
import com.example.whatsanki.dto.SourceMutationDto;
import com.example.whatsanki.dto.YouTubeSourceAcceptanceDto;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.repository.VideoSourceRepository;
import com.example.whatsanki.service.BrowserSourceService;
import com.example.whatsanki.service.SourceManagementService;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.document.PastedTextIngestionService;
import com.example.whatsanki.service.exception.BrowserSourceConflictException;
import com.example.whatsanki.service.exception.BrowserSourceNotFoundException;
import com.example.whatsanki.service.exception.BrowserSourceValidationException;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.service.util.SourceReferenceCodec;
import com.example.whatsanki.service.util.TextNormalizer;
import com.example.whatsanki.service.youtube.YouTubeSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class BrowserSourceServiceImpl implements BrowserSourceService {

    private final UserIdentityService userIdentityService;
    private final PastedTextIngestionService pastedTextIngestionService;
    private final YouTubeSourceService youTubeSourceService;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final VideoSourceRepository videoSourceRepository;
    private final SourceManagementService sourceManagementService;
    private final BackgroundJobService backgroundJobService;
    private final TextNormalizer textNormalizer;

    @Override
    public PastedTextSourceDto createPastedText(String googleSubject, String text) {
        UserId userId = resolveUser(googleSubject);
        PastedTextAcceptanceDto accepted = pastedTextIngestionService.acceptWebsite(userId, text);
        return new PastedTextSourceDto(
                accepted.sourceId().value(),
                SourceReferenceCodec.encode(accepted.sourceId()),
                PastedTextSourceDto.Status.RECEIVED,
                accepted.characterCount()
        );
    }

    @Override
    public AcceptedSourceDto createYouTube(String googleSubject, String url) {
        YouTubeSourceAcceptanceDto accepted;
        try {
            accepted = youTubeSourceService.acceptWebsite(resolveUser(googleSubject), url);
        } catch (IllegalArgumentException exception) {
            throw new BrowserSourceValidationException("INVALID_YOUTUBE_URL", HttpStatus.BAD_REQUEST);
        }
        return new AcceptedSourceDto(
                SourceReferenceCodec.encode(accepted.sourceId()),
                SourceDocumentEntity.Status.RECEIVED,
                accepted.existing()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public SourceListDto list(
            String googleSubject,
            int page,
            int pageSize,
            String search,
            SourceKind kind,
            SourceStatus status,
            SortField sort,
            SortDirection direction
    ) {
        UserId userId = resolveUser(googleSubject);
        // An empty text pattern matches every name and keeps PostgreSQL from inferring a null
        // search parameter as bytea inside lower(...).
        String normalizedSearch = search == null || search.isBlank() ? "" : search.strip();
        Sort.Direction springDirection = direction == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property = switch (sort) {
            case CREATED_AT -> "createdAt";
            case UPDATED_AT -> "updatedAt";
            case DISPLAY_NAME -> "displayName";
        };
        var result = sourceDocumentRepository.findBrowserPage(
                userId.value(),
                normalizedSearch,
                kind == null ? null : SourceDocumentEntity.Kind.valueOf(kind.name()),
                status == null ? null : SourceDocumentEntity.Status.valueOf(status.name()),
                PageRequest.of(page, pageSize, Sort.by(springDirection, property).and(Sort.by("id")))
        );
        return new SourceListDto(
                result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public BrowserSourceDetailsDto get(String googleSubject, String sourceReference) {
        UserId userId = resolveUser(googleSubject);
        SourceDocumentEntity source = findOwnedSource(userId, sourceReference);
        VideoSourceEntity video = source.getKind() == SourceDocumentEntity.Kind.YOUTUBE_VIDEO
                ? videoSourceRepository.findBySourceIdAndUserId(source.getId(), userId.value()).orElse(null)
                : null;
        String origin = String.valueOf(source.getMetadata().getOrDefault("origin", "UNKNOWN"));
        return new BrowserSourceDetailsDto(
                SourceReferenceCodec.encode(new SourceId(source.getId())),
                source.getKind(),
                source.getStatus(),
                source.getDisplayName(),
                source.getLanguage(),
                origin,
                source.getCharacterCount(),
                source.getPassageCount(),
                source.getErrorCode(),
                isRetryable(source),
                video == null ? null : new BrowserSourceDetailsDto.YouTubeDetailsDto(
                        video.getCanonicalUrl(),
                        video.getTitle(),
                        video.getChannelName(),
                        video.getDurationMs(),
                        video.getCaptionKind().name()
                ),
                source.getCreatedAt(),
                source.getUpdatedAt(),
                source.getVersion()
        );
    }

    @Override
    @Transactional
    public BrowserSourceSummaryDto rename(
            String googleSubject,
            String sourceReference,
            String displayName,
            long version
    ) {
        UserId userId = resolveUser(googleSubject);
        SourceDocumentEntity source = findOwnedSource(userId, sourceReference);
        if (source.getStatus() == SourceDocumentEntity.Status.DELETING
                || source.getStatus() == SourceDocumentEntity.Status.DELETED) {
            throw new BrowserSourceConflictException("SOURCE_NOT_EDITABLE");
        }
        if (source.getVersion() != version) {
            throw new BrowserSourceConflictException("SOURCE_VERSION_CONFLICT");
        }
        String normalizedName = textNormalizer.normalizeDisplay(displayName);
        if (normalizedName.isBlank()) {
            throw new BrowserSourceConflictException("SOURCE_NAME_INVALID");
        }
        source.setDisplayName(normalizedName);
        return toSummary(sourceDocumentRepository.saveAndFlush(source));
    }

    @Override
    @Transactional
    public SourceMutationDto retry(String googleSubject, String sourceReference) {
        UserId userId = resolveUser(googleSubject);
        SourceDocumentEntity source = findOwnedSource(userId, sourceReference);
        if (!isRetryable(source)) {
            throw new BrowserSourceConflictException("SOURCE_NOT_RETRYABLE");
        }
        SourceId sourceId = new SourceId(source.getId());
        BackgroundJobEntity.Type type;
        java.util.Map<String, Object> payload;
        if (source.getKind() == SourceDocumentEntity.Kind.PASTED_TEXT) {
            source.setStatus(SourceDocumentEntity.Status.RECEIVED);
            type = BackgroundJobEntity.Type.INGEST_TEXT;
            payload = IngestTextJobPayloadDto.create(sourceId).toMap();
        } else if (source.getKind() == SourceDocumentEntity.Kind.YOUTUBE_VIDEO) {
            source.setStatus(SourceDocumentEntity.Status.RECEIVED);
            type = BackgroundJobEntity.Type.FETCH_VIDEO_SUBTITLES;
            payload = FetchVideoSubtitlesJobPayloadDto.create(sourceId).toMap();
        } else {
            source.setStatus(SourceDocumentEntity.Status.STORED);
            type = BackgroundJobEntity.Type.EXTRACT_DOCUMENT;
            payload = ExtractDocumentJobPayloadDto.create(sourceId).toMap();
        }
        source.setErrorCode(null);
        source.setErrorDetailSafe(null);
        sourceDocumentRepository.save(source);
        JobId jobId = backgroundJobService.enqueue(userId, type, payload);
        return new SourceMutationDto(
                sourceReference,
                source.getStatus(),
                jobId.value().toString()
        );
    }

    @Override
    @Transactional
    public SourceMutationDto delete(String googleSubject, String sourceReference) {
        UserId userId = resolveUser(googleSubject);
        SourceDeletionRequestDto result = sourceManagementService.requestDeletion(userId, sourceReference);
        if (result.outcome() == SourceDeletionRequestDto.Outcome.NOT_FOUND_OR_DELETED) {
            throw new BrowserSourceNotFoundException();
        }
        return new SourceMutationDto(
                sourceReference,
                SourceDocumentEntity.Status.DELETING,
                result.jobId() == null ? null : result.jobId().value().toString()
        );
    }

    private UserId resolveUser(String googleSubject) {
        return userIdentityService
                .findUserId(UserIdentityEntity.Provider.GOOGLE, googleSubject)
                .orElseThrow(() -> new IllegalStateException("Authenticated Google identity is not linked"));
    }

    private SourceDocumentEntity findOwnedSource(UserId userId, String sourceReference) {
        SourceId sourceId = SourceReferenceCodec.decode(sourceReference)
                .orElseThrow(BrowserSourceNotFoundException::new);
        return sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value())
                .filter(source -> source.getStatus() != SourceDocumentEntity.Status.DELETED)
                .orElseThrow(BrowserSourceNotFoundException::new);
    }

    private BrowserSourceSummaryDto toSummary(SourceDocumentEntity source) {
        return new BrowserSourceSummaryDto(
                SourceReferenceCodec.encode(new SourceId(source.getId())),
                source.getKind(),
                source.getStatus(),
                source.getDisplayName(),
                source.getLanguage(),
                source.getCharacterCount(),
                source.getPassageCount(),
                source.getErrorCode(),
                isRetryable(source),
                source.getCreatedAt(),
                source.getUpdatedAt(),
                source.getVersion()
        );
    }

    private boolean isRetryable(SourceDocumentEntity source) {
        if (source.getStatus() != SourceDocumentEntity.Status.FAILED) {
            return false;
        }
        if (source.getKind() == SourceDocumentEntity.Kind.PASTED_TEXT) {
            return source.getStagedTextCiphertext() != null;
        }
        if (source.getKind() == SourceDocumentEntity.Kind.YOUTUBE_VIDEO) {
            return Set.of(
                    "YOUTUBE_PROVIDER_UNAVAILABLE",
                    "YOUTUBE_PROCESS_TIMEOUT",
                    "YOUTUBE_SUBTITLE_DOWNLOAD_FAILED"
            ).contains(source.getErrorCode());
        }
        return source.getObjectKey() != null && Set.of(
                "EXTRACTION_TIMEOUT",
                "EXTRACTION_INTERRUPTED",
                "SOURCE_OBJECT_READ_FAILED"
        ).contains(source.getErrorCode());
    }
}
