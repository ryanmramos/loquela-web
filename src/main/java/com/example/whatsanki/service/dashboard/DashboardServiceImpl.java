package com.example.whatsanki.service.dashboard;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.DashboardDto;
import com.example.whatsanki.dto.ActivityItemDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.CardRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.DashboardService;
import com.example.whatsanki.service.BrowserActivityService;
import com.example.whatsanki.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final UserIdentityService userIdentityService;
    private final AppUserRepository appUserRepository;
    private final CardRepository cardRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final BrowserActivityService browserActivityService;

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
                        browserActivityService.listByUser(
                                userId, null, ActivityItemDto.Lifecycle.ACTIVE, null, 1
                        ).activeCount()
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
        return new DashboardDto.ActivitySummary(
                DashboardDto.SectionStatus.AVAILABLE,
                browserActivityService.listByUser(userId, null, null, null, ACTIVITY_LIMIT).items()
        );
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
