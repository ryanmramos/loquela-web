package com.example.whatsanki.service.account;

import com.example.whatsanki.domain.AccountDeletionRequestEntity;
import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.AccountDeletionConfirmationResultDto;
import com.example.whatsanki.dto.AccountDeletionRequestResultDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.AccountDeletionRequestRepository;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.service.storage.ObjectStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceImplTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private AccountDeletionRequestRepository accountDeletionRequestRepository;
    @Mock private BackgroundJobRepository backgroundJobRepository;
    @Mock private BackgroundJobService backgroundJobService;
    @Mock private AccountDeletionCleanupService accountDeletionCleanupService;
    @Mock private ObjectStore objectStore;

    private AccountDeletionServiceImpl service;

    @BeforeEach
    void setUp() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.getAccountDeletion().setConfirmationLifetime(Duration.ofMinutes(10));
        service = new AccountDeletionServiceImpl(
                appUserRepository,
                accountDeletionRequestRepository,
                backgroundJobRepository,
                backgroundJobService,
                accountDeletionCleanupService,
                objectStore,
                configuration
        );
    }

    @Test
    void storesOnlyAHashWhenIssuingAShortLivedConfirmationCode() {
        UserId userId = UserId.random();
        AccountDeletionRequestEntity request = new AccountDeletionRequestEntity();
        when(appUserRepository.findById(userId.value())).thenReturn(Optional.of(activeUser(userId)));
        when(accountDeletionRequestRepository.findByUserIdForUpdate(userId.value())).thenReturn(Optional.of(request));

        AccountDeletionRequestResultDto result = service.request(userId);

        assertThat(result.outcome()).isEqualTo(AccountDeletionRequestResultDto.Outcome.CONFIRMATION_REQUIRED);
        assertThat(result.confirmationCode()).hasSize(8).matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}");
        assertThat(request.getConfirmationCodeHash()).isNotEqualTo(result.confirmationCode().getBytes());
        assertThat(request.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofMinutes(9)));
        verify(accountDeletionRequestRepository).save(request);
    }

    @Test
    void confirmsValidCodeThenCancelsPendingWorkAndQueuesIdOnlyDeletionJob() {
        UserId userId = UserId.random();
        AccountDeletionRequestEntity request = new AccountDeletionRequestEntity();
        AppUserEntity user = activeUser(userId);
        when(appUserRepository.findById(userId.value())).thenReturn(Optional.of(user));
        when(accountDeletionRequestRepository.findByUserIdForUpdate(userId.value())).thenReturn(Optional.of(request));
        String code = service.request(userId).confirmationCode();
        when(backgroundJobService.enqueue(eq(userId), eq(BackgroundJobEntity.Type.DELETE_USER_DATA), any()))
                .thenReturn(JobId.random());

        AccountDeletionConfirmationResultDto result = service.confirm(userId, code);

        assertThat(result.outcome()).isEqualTo(AccountDeletionConfirmationResultDto.Outcome.QUEUED);
        assertThat(user.getStatus()).isEqualTo(AppUserEntity.Status.DELETING);
        ArgumentCaptor<java.util.Map<String, Object>> payload = ArgumentCaptor.forClass(java.util.Map.class);
        verify(backgroundJobService).enqueue(eq(userId), eq(BackgroundJobEntity.Type.DELETE_USER_DATA), payload.capture());
        assertThat(payload.getValue()).containsOnlyKeys("schemaVersion", "userId");
        verify(backgroundJobRepository).cancelPendingForUser(eq(userId.value()), eq(null), any(Instant.class));
    }

    @Test
    void rejectsAnIncorrectCodeWithoutChangingAccountStateOrQueueingWork() {
        UserId userId = UserId.random();
        AppUserEntity user = activeUser(userId);
        AccountDeletionRequestEntity request = new AccountDeletionRequestEntity();
        when(appUserRepository.findById(userId.value())).thenReturn(Optional.of(user));
        when(accountDeletionRequestRepository.findByUserIdForUpdate(userId.value())).thenReturn(Optional.of(request));
        service.request(userId);

        assertThat(service.confirm(userId, "ABCDEFGH").outcome())
                .isEqualTo(AccountDeletionConfirmationResultDto.Outcome.INVALID_OR_EXPIRED);
        assertThat(user.getStatus()).isEqualTo(AppUserEntity.Status.ACTIVE);
        verify(backgroundJobService, never()).enqueue(any(), any(), any());
    }

    @Test
    void browserConfirmationQueuesDeletionWithoutIssuingAWhatsAppCode() {
        UserId userId = UserId.random();
        AppUserEntity user = activeUser(userId);
        when(appUserRepository.findById(userId.value())).thenReturn(Optional.of(user));
        when(backgroundJobService.enqueue(eq(userId), eq(BackgroundJobEntity.Type.DELETE_USER_DATA), any()))
                .thenReturn(JobId.random());

        AccountDeletionConfirmationResultDto result = service.confirmBrowser(userId);

        assertThat(result.outcome()).isEqualTo(AccountDeletionConfirmationResultDto.Outcome.QUEUED);
        assertThat(user.getStatus()).isEqualTo(AppUserEntity.Status.DELETING);
        verify(accountDeletionRequestRepository, never()).save(any());
    }

    private AppUserEntity activeUser(UserId userId) {
        AppUserEntity user = new AppUserEntity();
        user.setId(userId.value());
        user.setStatus(AppUserEntity.Status.ACTIVE);
        return user;
    }
}
