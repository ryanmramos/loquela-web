package com.example.whatsanki.service.account;

import com.example.whatsanki.domain.AccountDeletionRequestEntity;
import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.AccountDeletionConfirmationResultDto;
import com.example.whatsanki.dto.AccountDeletionRequestResultDto;
import com.example.whatsanki.dto.DeleteUserDataJobPayloadDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.AccountDeletionRequestRepository;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.service.AccountDeletionService;
import com.example.whatsanki.service.exception.AccountDeletionException;
import com.example.whatsanki.service.exception.ObjectStoreException;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.service.storage.ObjectStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionServiceImpl implements AccountDeletionService {

    private static final char[] CONFIRMATION_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final int CONFIRMATION_CODE_LENGTH = 8;

    private final AppUserRepository appUserRepository;
    private final AccountDeletionRequestRepository accountDeletionRequestRepository;
    private final BackgroundJobRepository backgroundJobRepository;
    private final BackgroundJobService backgroundJobService;
    private final AccountDeletionCleanupService accountDeletionCleanupService;
    private final ObjectStore objectStore;
    private final WhatsAnkiConfigurationDto configuration;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public AccountDeletionRequestResultDto request(UserId userId) {
        AppUserEntity user = findUser(userId);
        if (user.getStatus() == AppUserEntity.Status.DELETING) {
            return AccountDeletionRequestResultDto.deletionInProgress();
        }

        String code = generateConfirmationCode();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(configuration.getAccountDeletion().getConfirmationLifetime());
        AccountDeletionRequestEntity request = accountDeletionRequestRepository.findByUserIdForUpdate(userId.value())
                .orElseGet(AccountDeletionRequestEntity::new);
        request.setUserId(userId.value());
        request.setConfirmationCodeHash(hash(code));
        request.setExpiresAt(expiresAt);
        accountDeletionRequestRepository.save(request);

        // Do not include the code in logs; it is returned exactly once in the TwiML reply.
        log.info("Issued account deletion confirmation userId={} expiresAt={}", userId.value(), expiresAt);
        return AccountDeletionRequestResultDto.confirmationRequired(code, expiresAt);
    }

    @Override
    @Transactional
    public AccountDeletionConfirmationResultDto confirm(UserId userId, String confirmationCode) {
        AppUserEntity user = findUser(userId);
        if (user.getStatus() == AppUserEntity.Status.DELETING) {
            return AccountDeletionConfirmationResultDto.deletionInProgress();
        }

        Instant now = Instant.now();
        var request = accountDeletionRequestRepository.findByUserIdForUpdate(userId.value());
        if (request.isEmpty() || !request.orElseThrow().getExpiresAt().isAfter(now)
                || !MessageDigest.isEqual(request.orElseThrow().getConfirmationCodeHash(), hash(confirmationCode))) {
            // Deliberately use one outcome for missing, expired, and incorrect codes.
            // This avoids exposing whether a pending destructive request exists.
            log.info("Rejected account deletion confirmation userId={}", userId.value());
            return AccountDeletionConfirmationResultDto.invalidOrExpired();
        }

        user.setStatus(AppUserEntity.Status.DELETING);
        appUserRepository.save(user);
        int cancelledJobs = backgroundJobRepository.cancelPendingForUser(userId.value(), null, now);
        JobId jobId = backgroundJobService.enqueue(
                userId,
                BackgroundJobEntity.Type.DELETE_USER_DATA,
                DeleteUserDataJobPayloadDto.create(userId).toMap()
        );
        log.info(
                "Queued confirmed account deletion userId={} jobId={} cancelledJobCount={}",
                userId.value(), jobId.value(), cancelledJobs
        );
        return AccountDeletionConfirmationResultDto.queued(jobId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isDeletionInProgress(UserId userId) {
        return appUserRepository.findById(userId.value())
                .map(user -> user.getStatus() == AppUserEntity.Status.DELETING)
                .orElse(false);
    }

    @Override
    public void delete(UserId userId, JobId deletionJobId) {
        Set<String> objectKeys = accountDeletionCleanupService.collectObjectKeys(userId, deletionJobId);
        for (String objectKey : objectKeys) {
            try {
                // Object-store deletes are idempotent. A retry after a worker crash can
                // safely repeat this loop before the database cascade removes the user.
                objectStore.delete(objectKey);
            } catch (ObjectStoreException exception) {
                throw new AccountDeletionException(
                        BackgroundJobFailureKind.TRANSIENT,
                        "USER_DATA_OBJECT_DELETE_FAILED",
                        exception
                );
            }
        }
        accountDeletionCleanupService.deleteUserAfterObjects(userId, deletionJobId);
        log.info("Completed account deletion userId={} objectCount={}", userId.value(), objectKeys.size());
    }

    private AppUserEntity findUser(UserId userId) {
        return appUserRepository.findById(userId.value())
                .orElseThrow(() -> new AccountDeletionException(BackgroundJobFailureKind.PERMANENT, "USER_NOT_FOUND"));
    }

    private String generateConfirmationCode() {
        char[] code = new char[CONFIRMATION_CODE_LENGTH];
        for (int index = 0; index < code.length; index++) {
            code[index] = CONFIRMATION_ALPHABET[secureRandom.nextInt(CONFIRMATION_ALPHABET.length)];
        }
        return new String(code);
    }

    private byte[] hash(String code) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
