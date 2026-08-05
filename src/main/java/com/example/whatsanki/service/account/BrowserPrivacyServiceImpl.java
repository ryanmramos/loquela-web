package com.example.whatsanki.service.account;

import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.dto.BrowserAccountDeletionDto;
import com.example.whatsanki.dto.BrowserAccountDeletionStatusDto;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.service.AccountDeletionService;
import com.example.whatsanki.service.BrowserPrivacyService;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.exception.BrowserPrivacyException;
import com.example.whatsanki.service.util.BrowserReferenceCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BrowserPrivacyServiceImpl implements BrowserPrivacyService {
    private final UserIdentityService userIdentityService;
    private final AccountDeletionService accountDeletionService;
    private final BackgroundJobRepository backgroundJobRepository;

    @Override
    public BrowserAccountDeletionDto deleteAccount(String googleSubject) {
        var userId = userIdentityService.findUserId(
                UserIdentityEntity.Provider.GOOGLE, googleSubject
        ).orElseThrow(() -> new BrowserPrivacyException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"));
        var result = accountDeletionService.confirmBrowser(userId);
        String jobReference = result.jobId() == null
                ? null
                : BrowserReferenceCodec.encode(result.jobId().value());
        return new BrowserAccountDeletionDto("DELETING", jobReference, Instant.now());
    }

    @Override
    public BrowserAccountDeletionStatusDto currentDeletion(String googleSubject) {
        var userId = userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, googleSubject)
                .orElseThrow(() -> new BrowserPrivacyException(HttpStatus.NOT_FOUND, "ACCOUNT_DELETED"));
        var job = backgroundJobRepository.findFirstByUserIdAndTypeOrderByCreatedAtDesc(
                userId.value(), BackgroundJobEntity.Type.DELETE_USER_DATA
        ).orElseThrow(() -> new BrowserPrivacyException(HttpStatus.NOT_FOUND, "DELETION_NOT_FOUND"));
        BrowserAccountDeletionStatusDto.Status status = switch (job.getState()) {
            case QUEUED, RETRY_WAIT -> BrowserAccountDeletionStatusDto.Status.QUEUED;
            case RUNNING -> BrowserAccountDeletionStatusDto.Status.IN_PROGRESS;
            case DEAD, CANCELLED, SUCCEEDED -> BrowserAccountDeletionStatusDto.Status.NEEDS_ATTENTION;
        };
        String supportCode = status == BrowserAccountDeletionStatusDto.Status.NEEDS_ATTENTION
                ? BrowserReferenceCodec.encode(job.getId())
                : null;
        return new BrowserAccountDeletionStatusDto(status, job.getUpdatedAt(), supportCode);
    }
}
