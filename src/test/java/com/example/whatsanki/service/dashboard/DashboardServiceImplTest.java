package com.example.whatsanki.service.dashboard;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.ActivityItemDto;
import com.example.whatsanki.dto.ActivityPageDto;
import com.example.whatsanki.dto.DashboardDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.CardRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.BrowserActivityService;
import com.example.whatsanki.service.UserIdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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
    @Mock private BrowserActivityService browserActivityService;

    private DashboardServiceImpl service;
    private UserId userId;

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl(
                userIdentityService,
                appUserRepository,
                cardRepository,
                sourceDocumentRepository,
                browserActivityService
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
        when(browserActivityService.listByUser(userId, null, ActivityItemDto.Lifecycle.ACTIVE, null, 1))
                .thenReturn(new ActivityPageDto(List.of(), null, 0));
        when(browserActivityService.listByUser(userId, null, null, null, 6))
                .thenReturn(new ActivityPageDto(List.of(), null, 0));
    }

    @Test
    void aggregatesCountsAndUsesLogicalActivityProjection() {
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
        when(sourceDocumentRepository.totalCorpusStoredBytesByUserId(userId.value())).thenReturn(1_572_864L);
        ActivityItemDto activity = activity();
        when(browserActivityService.listByUser(userId, null, ActivityItemDto.Lifecycle.ACTIVE, null, 1))
                .thenReturn(new ActivityPageDto(List.of(activity), null, 3));
        when(browserActivityService.listByUser(userId, null, null, null, 6))
                .thenReturn(new ActivityPageDto(List.of(activity), null, 3));

        DashboardDto result = service.getByGoogleSubject("google-subject");

        assertThat(result.cards().cardsToStudy()).isEqualTo(12);
        assertThat(result.cards().totalCards()).isEqualTo(384);
        assertThat(result.sources().readySources()).isEqualTo(24);
        assertThat(result.storage().usedBytes()).isEqualTo(1_572_864L);
        assertThat(result.jobs().activeJobs()).isEqualTo(3);
        assertThat(result.activity().items()).containsExactly(activity);
    }

    @Test
    void reportsOneUnavailableSectionWithoutDiscardingTheRest() {
        when(cardRepository.countByUserIdAndLastPracticedAtIsNull(userId.value()))
                .thenThrow(new IllegalStateException("card projection unavailable"));

        DashboardDto result = service.getByGoogleSubject("google-subject");

        assertThat(result.cards().status()).isEqualTo(DashboardDto.SectionStatus.UNAVAILABLE);
        assertThat(result.sources().status()).isEqualTo(DashboardDto.SectionStatus.AVAILABLE);
        assertThat(result.activity().status()).isEqualTo(DashboardDto.SectionStatus.AVAILABLE);
    }

    @Test
    void isolatesUnavailableActivityFromLogicalCount() {
        when(browserActivityService.listByUser(userId, null, null, null, 6))
                .thenThrow(new IllegalStateException("activity unavailable"));

        DashboardDto result = service.getByGoogleSubject("google-subject");

        assertThat(result.activity().status()).isEqualTo(DashboardDto.SectionStatus.UNAVAILABLE);
        assertThat(result.jobs().status()).isEqualTo(DashboardDto.SectionStatus.AVAILABLE);
    }

    private ActivityItemDto activity() {
        Instant now = Instant.parse("2026-08-04T20:00:00Z");
        return new ActivityItemDto(
                "act_src_reference",
                ActivityItemDto.Category.SOURCE,
                ActivityItemDto.Operation.INGEST_SOURCE,
                ActivityItemDto.Lifecycle.ACTIVE,
                ActivityItemDto.Status.EXTRACTING,
                "Spanish notes",
                null,
                now.minusSeconds(60),
                now,
                "/app/sources/reference"
        );
    }
}
