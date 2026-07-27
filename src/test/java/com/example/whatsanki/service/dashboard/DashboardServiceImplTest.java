package com.example.whatsanki.service.dashboard;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.DashboardDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.repository.CardRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.util.SourceReferenceCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock private UserIdentityService userIdentityService;
    @Mock private AppUserRepository appUserRepository;
    @Mock private CardRepository cardRepository;
    @Mock private SourceDocumentRepository sourceDocumentRepository;
    @Mock private BackgroundJobRepository backgroundJobRepository;

    private DashboardServiceImpl service;
    private UserId userId;

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl(
                userIdentityService,
                appUserRepository,
                cardRepository,
                sourceDocumentRepository,
                backgroundJobRepository
        );
        userId = UserId.random();
        AppUserEntity user = new AppUserEntity();
        user.setId(userId.value());
        user.setStatus(AppUserEntity.Status.ACTIVE);
        user.setSourceLanguage("es");
        user.setDefinitionLanguage("en");
        when(userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, "google-subject"))
                .thenReturn(Optional.of(userId));
        when(appUserRepository.findByIdAndStatus(userId.value(), AppUserEntity.Status.ACTIVE))
                .thenReturn(Optional.of(user));
    }

    @Test
    void aggregatesCountsAndBoundsNewestActivity() {
        when(cardRepository.countByUserIdAndLastPracticedAtIsNull(userId.value())).thenReturn(12L);
        when(cardRepository.countByUserId(userId.value())).thenReturn(384L);
        when(cardRepository.countByUserIdAndCreatedAtGreaterThanEqual(eq(userId.value()), any(Instant.class)))
                .thenReturn(16L);
        when(sourceDocumentRepository.countByUserIdAndStatusNot(
                userId.value(), SourceDocumentEntity.Status.DELETED
        )).thenReturn(26L);
        when(sourceDocumentRepository.countByUserIdAndStatus(
                userId.value(), SourceDocumentEntity.Status.READY
        )).thenReturn(24L);
        when(sourceDocumentRepository.countByUserIdAndStatusIn(eq(userId.value()), any(Set.class)))
                .thenReturn(2L);
        when(backgroundJobRepository.countByUserIdAndStateIn(eq(userId.value()), any(Set.class)))
                .thenReturn(3L);
        SourceDocumentEntity source = source(Instant.parse("2026-07-26T20:00:00Z"));
        BackgroundJobEntity job = job(Instant.parse("2026-07-26T21:00:00Z"));
        when(sourceDocumentRepository.findAllByUserIdOrderByCreatedAtDesc(
                userId.value(), PageRequest.of(0, 6)
        )).thenReturn(List.of(source));
        when(backgroundJobRepository.findRecentByUserId(userId.value(), 6)).thenReturn(List.of(job));

        DashboardDto result = service.getByGoogleSubject("google-subject");

        assertThat(result.cards().cardsToStudy()).isEqualTo(12);
        assertThat(result.cards().totalCards()).isEqualTo(384);
        assertThat(result.sources().readySources()).isEqualTo(24);
        assertThat(result.jobs().activeJobs()).isEqualTo(3);
        assertThat(result.languagePair().sourceLanguage()).isEqualTo("es");
        assertThat(result.activity().items())
                .extracting(DashboardDto.ActivityItem::kind)
                .containsExactly(DashboardDto.ActivityKind.JOB, DashboardDto.ActivityKind.SOURCE);
        assertThat(result.activity().items().get(1).target())
                .isEqualTo("/app/sources/" + SourceReferenceCodec.encode(new SourceId(source.getId())));
    }

    @Test
    void reportsOneUnavailableSectionWithoutDiscardingTheRest() {
        when(cardRepository.countByUserIdAndLastPracticedAtIsNull(userId.value()))
                .thenThrow(new IllegalStateException("card projection unavailable"));
        when(sourceDocumentRepository.countByUserIdAndStatusNot(
                userId.value(), SourceDocumentEntity.Status.DELETED
        )).thenReturn(0L);
        when(sourceDocumentRepository.countByUserIdAndStatus(
                userId.value(), SourceDocumentEntity.Status.READY
        )).thenReturn(0L);
        when(sourceDocumentRepository.countByUserIdAndStatusIn(eq(userId.value()), any(Set.class)))
                .thenReturn(0L);
        when(backgroundJobRepository.countByUserIdAndStateIn(eq(userId.value()), any(Set.class)))
                .thenReturn(0L);
        when(sourceDocumentRepository.findAllByUserIdOrderByCreatedAtDesc(
                userId.value(), PageRequest.of(0, 6)
        )).thenReturn(List.of());
        when(backgroundJobRepository.findRecentByUserId(userId.value(), 6)).thenReturn(List.of());

        DashboardDto result = service.getByGoogleSubject("google-subject");

        assertThat(result.cards().status()).isEqualTo(DashboardDto.SectionStatus.UNAVAILABLE);
        assertThat(result.cards().totalCards()).isNull();
        assertThat(result.sources().status()).isEqualTo(DashboardDto.SectionStatus.AVAILABLE);
        assertThat(result.sources().totalSources()).isZero();
        assertThat(result.activity().items()).isEmpty();
    }

    @Test
    void safelyMapsEverySourceAndJobActivityType() {
        List<SourceDocumentEntity> sources = Arrays.stream(SourceDocumentEntity.Kind.values())
                .map(kind -> source(kind, Instant.parse("2026-07-26T20:00:00Z")))
                .toList();
        List<BackgroundJobEntity> jobs = Arrays.stream(BackgroundJobEntity.Type.values())
                .map(type -> job(type, Instant.parse("2026-07-26T21:00:00Z")))
                .toList();
        when(sourceDocumentRepository.findAllByUserIdOrderByCreatedAtDesc(
                userId.value(), PageRequest.of(0, 6)
        )).thenReturn(sources);
        when(backgroundJobRepository.findRecentByUserId(userId.value(), 6)).thenReturn(jobs);

        DashboardDto result = service.getByGoogleSubject("google-subject");

        assertThat(result.activity().status()).isEqualTo(DashboardDto.SectionStatus.AVAILABLE);
        assertThat(result.activity().items()).hasSize(6);
        assertThat(result.activity().items())
                .allSatisfy(item -> {
                    assertThat(item.title()).isNotBlank();
                    assertThat(item.detail()).isNotBlank();
                    assertThat(item.target()).startsWith("/app/");
                });
    }

    private SourceDocumentEntity source(Instant updatedAt) {
        return source(SourceDocumentEntity.Kind.PASTED_TEXT, updatedAt);
    }

    private SourceDocumentEntity source(SourceDocumentEntity.Kind kind, Instant updatedAt) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(SourceId.random().value());
        source.setKind(kind);
        source.setDisplayName("News article");
        source.setStatus(SourceDocumentEntity.Status.READY);
        source.setUpdatedAt(updatedAt);
        return source;
    }

    private BackgroundJobEntity job(Instant updatedAt) {
        return job(BackgroundJobEntity.Type.INGEST_TEXT, updatedAt);
    }

    private BackgroundJobEntity job(BackgroundJobEntity.Type type, Instant updatedAt) {
        BackgroundJobEntity job = new BackgroundJobEntity();
        job.setType(type);
        job.setState(BackgroundJobEntity.State.RUNNING);
        job.setUpdatedAt(updatedAt);
        return job;
    }
}
