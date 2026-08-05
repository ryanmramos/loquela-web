package com.example.whatsanki.service.account;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.BrowserAccountDeletionStatusDto;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.service.AccountDeletionService;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.util.BrowserReferenceCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrowserPrivacyServiceImplTest {
    @Mock private UserIdentityService userIdentityService;
    @Mock private AccountDeletionService accountDeletionService;
    @Mock private BackgroundJobRepository backgroundJobRepository;
    @InjectMocks private BrowserPrivacyServiceImpl service;

    @Test
    void mapsActiveCleanupWithoutExposingAnInternalIdentifier() {
        BackgroundJobEntity job = job(BackgroundJobEntity.State.RUNNING);
        stubJob(job);

        BrowserAccountDeletionStatusDto result = service.currentDeletion("google-subject");

        assertThat(result.status()).isEqualTo(BrowserAccountDeletionStatusDto.Status.IN_PROGRESS);
        assertThat(result.supportCode()).isNull();
        assertThat(result.updatedAt()).isEqualTo(job.getUpdatedAt());
    }

    @Test
    void mapsExhaustedCleanupToAttentionWithAnOpaqueSupportCode() {
        BackgroundJobEntity job = job(BackgroundJobEntity.State.DEAD);
        stubJob(job);

        BrowserAccountDeletionStatusDto result = service.currentDeletion("google-subject");

        assertThat(result.status()).isEqualTo(BrowserAccountDeletionStatusDto.Status.NEEDS_ATTENTION);
        assertThat(result.supportCode()).isEqualTo(BrowserReferenceCodec.encode(job.getId()));
        assertThat(result.supportCode()).doesNotContain(job.getId().toString());
    }

    private void stubJob(BackgroundJobEntity job) {
        UserId userId = new UserId(UUID.randomUUID());
        when(userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, "google-subject"))
                .thenReturn(Optional.of(userId));
        when(backgroundJobRepository.findFirstByUserIdAndTypeOrderByCreatedAtDesc(
                userId.value(), BackgroundJobEntity.Type.DELETE_USER_DATA
        )).thenReturn(Optional.of(job));
    }

    private BackgroundJobEntity job(BackgroundJobEntity.State state) {
        BackgroundJobEntity job = new BackgroundJobEntity();
        job.setId(UUID.randomUUID());
        job.setType(BackgroundJobEntity.Type.DELETE_USER_DATA);
        job.setState(state);
        job.setUpdatedAt(Instant.parse("2026-08-04T20:00:00Z"));
        return job;
    }
}
