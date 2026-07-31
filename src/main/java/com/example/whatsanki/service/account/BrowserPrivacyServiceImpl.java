package com.example.whatsanki.service.account;

import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.BrowserAccountDeletionDto;
import com.example.whatsanki.service.AccountDeletionService;
import com.example.whatsanki.service.BrowserPrivacyService;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.exception.BrowserPrivacyException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BrowserPrivacyServiceImpl implements BrowserPrivacyService {
    private final UserIdentityService userIdentityService;
    private final AccountDeletionService accountDeletionService;

    @Override
    public BrowserAccountDeletionDto deleteAccount(String googleSubject) {
        var userId = userIdentityService.findUserId(
                UserIdentityEntity.Provider.GOOGLE, googleSubject
        ).orElseThrow(() -> new BrowserPrivacyException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"));
        var result = accountDeletionService.confirmBrowser(userId);
        String jobReference = result.jobId() == null ? null : result.jobId().value().toString();
        return new BrowserAccountDeletionDto("DELETING", jobReference, Instant.now());
    }
}
