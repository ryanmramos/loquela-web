package com.example.whatsanki.service.dashboard;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.DashboardDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.repository.CardRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.DashboardService;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.util.SourceReferenceCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private static final int ACTIVITY_LIMIT = 6;
    private static final Set<SourceDocumentEntity.Status> PROCESSING_SOURCE_STATES = Set.of(
            SourceDocumentEntity.Status.RECEIVED,
            SourceDocumentEntity.Status.FETCHING,
            SourceDocumentEntity.Status.STORED,
            SourceDocumentEntity.Status.EXTRACTING,
            SourceDocumentEntity.Status.INDEXING,
            SourceDocumentEntity.Status.DELETING
    );
    private static final Set<BackgroundJobEntity.State> ACTIVE_JOB_STATES = Set.of(
            BackgroundJobEntity.State.QUEUED,
            BackgroundJobEntity.State.RUNNING,
            BackgroundJobEntity.State.RETRY_WAIT
    );

    private final UserIdentityService userIdentityService;
    private final AppUserRepository appUserRepository;
    private final CardRepository cardRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final BackgroundJobRepository backgroundJobRepository;

    @Override
    public DashboardDto getByGoogleSubject(String googleSubject) {
        UserId userId = userIdentityService
                .findUserId(UserIdentityEntity.Provider.GOOGLE, googleSubject)
                .orElseThrow(() -> new IllegalStateException("Authenticated Google identity is not linked"));
        AppUserEntity user = appUserRepository.findByIdAndStatus(userId.value(), AppUserEntity.Status.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Active browser user does not exist"));
        Instant generatedAt = Instant.now();

        DashboardDto.CardSummary cards = section(
                "cards",
                () -> new DashboardDto.CardSummary(
                        DashboardDto.SectionStatus.AVAILABLE,
                        cardRepository.countByUserIdAndLastPracticedAtIsNull(userId.value()),
                        cardRepository.countByUserId(userId.value()),
                        cardRepository.countByUserIdAndCreatedAtGreaterThanEqual(
                                userId.value(), generatedAt.minus(7, ChronoUnit.DAYS)
                        )
                ),
                DashboardDto.CardSummary::unavailable
        );
        DashboardDto.SourceSummary sources = section(
                "sources",
                () -> new DashboardDto.SourceSummary(
                        DashboardDto.SectionStatus.AVAILABLE,
                        sourceDocumentRepository.countByUserIdAndStatusNot(
                                userId.value(), SourceDocumentEntity.Status.DELETED
                        ),
                        sourceDocumentRepository.countByUserIdAndStatus(
                                userId.value(), SourceDocumentEntity.Status.READY
                        ),
                        sourceDocumentRepository.countByUserIdAndStatusIn(
                                userId.value(), PROCESSING_SOURCE_STATES
                        )
                ),
                DashboardDto.SourceSummary::unavailable
        );
        DashboardDto.JobSummary jobs = section(
                "jobs",
                () -> new DashboardDto.JobSummary(
                        DashboardDto.SectionStatus.AVAILABLE,
                        backgroundJobRepository.countByUserIdAndStateIn(userId.value(), ACTIVE_JOB_STATES)
                ),
                DashboardDto.JobSummary::unavailable
        );
        DashboardDto.StorageSummary storage = section(
                "storage",
                () -> new DashboardDto.StorageSummary(
                        DashboardDto.SectionStatus.AVAILABLE,
                        sourceDocumentRepository.totalCorpusStoredBytesByUserId(userId.value())
                ),
                DashboardDto.StorageSummary::unavailable
        );
        DashboardDto.ActivitySummary activity = section(
                "activity",
                () -> loadActivity(userId),
                DashboardDto.ActivitySummary::unavailable
        );

        return new DashboardDto(
                generatedAt,
                new DashboardDto.LanguagePair(user.getSourceLanguage(), user.getDefinitionLanguage()),
                cards,
                sources,
                storage,
                jobs,
                activity
        );
    }

    private DashboardDto.ActivitySummary loadActivity(UserId userId) {
        List<DashboardDto.ActivityItem> items = new ArrayList<>();
        sourceDocumentRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId.value(), PageRequest.of(0, ACTIVITY_LIMIT))
                .stream()
                .filter(source -> source.getStatus() != SourceDocumentEntity.Status.DELETED)
                .map(this::sourceActivity)
                .forEach(items::add);
        backgroundJobRepository.findRecentByUserId(userId.value(), ACTIVITY_LIMIT)
                .stream()
                .map(this::jobActivity)
                .forEach(items::add);
        List<DashboardDto.ActivityItem> recentItems = items.stream()
                .sorted(Comparator.comparing(DashboardDto.ActivityItem::occurredAt).reversed())
                .limit(ACTIVITY_LIMIT)
                .toList();
        return new DashboardDto.ActivitySummary(DashboardDto.SectionStatus.AVAILABLE, recentItems);
    }

    private DashboardDto.ActivityItem sourceActivity(SourceDocumentEntity source) {
        return new DashboardDto.ActivityItem(
                DashboardDto.ActivityKind.SOURCE,
                source.getDisplayName(),
                sourceDetail(source),
                source.getStatus().name(),
                source.getUpdatedAt(),
                "/app/sources/" + SourceReferenceCodec.encode(new com.example.whatsanki.domain.SourceId(source.getId()))
        );
    }

    private DashboardDto.ActivityItem jobActivity(BackgroundJobEntity job) {
        return new DashboardDto.ActivityItem(
                DashboardDto.ActivityKind.JOB,
                jobTitle(job.getType()),
                "Background activity",
                job.getState().name(),
                job.getUpdatedAt(),
                "/app/activity"
        );
    }

    private String sourceDetail(SourceDocumentEntity source) {
        return switch (source.getKind()) {
            case PASTED_TEXT -> "Pasted text source";
            case YOUTUBE_VIDEO -> "YouTube source";
            case PDF -> "PDF source";
            case TXT -> "Text document";
            case EPUB -> "EPUB source";
        };
    }

    private String jobTitle(BackgroundJobEntity.Type type) {
        return switch (type) {
            case INGEST_TEXT -> "Indexing pasted text";
            case FETCH_VIDEO_SUBTITLES -> "Fetching video subtitles";
            case FETCH_MEDIA, EXTRACT_DOCUMENT -> "Processing document";
            case LOOKUP -> "Running lookup";
            case EXPORT_DECK -> "Preparing deck export";
            case DELETE_SOURCE, PURGE_SOURCE_OBJECT, DELETE_TWILIO_MEDIA -> "Cleaning up source data";
            case DELETE_USER_DATA -> "Deleting account data";
            case DELIVER_LOOKUP_RESULT -> "Preparing lookup result";
        };
    }

    private <T> T section(String sectionName, Supplier<T> loader, Supplier<T> unavailable) {
        try {
            return loader.get();
        } catch (RuntimeException exception) {
            // Each aggregate section fails closed so one unavailable statistic does not
            // erase otherwise useful dashboard data.
            log.warn(
                    "Dashboard section unavailable section={} errorType={}",
                    sectionName,
                    exception.getClass().getSimpleName()
            );
            return unavailable.get();
        }
    }
}
