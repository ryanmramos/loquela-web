package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.BrowserLanguageSettingsDto;
import com.example.whatsanki.dto.LanguagePairUpdateResultDto;
import com.example.whatsanki.service.BrowserLanguageSettingsService;
import com.example.whatsanki.service.LanguagePairSupport;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.UserLanguageSettingsService;
import com.example.whatsanki.service.exception.UnsupportedBrowserLanguagePairException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrowserLanguageSettingsServiceImpl implements BrowserLanguageSettingsService {

    private final UserIdentityService userIdentityService;
    private final UserLanguageSettingsService userLanguageSettingsService;
    private final LanguagePairSupport languagePairSupport;

    @Override
    @Transactional(readOnly = true)
    public BrowserLanguageSettingsDto getByGoogleSubject(String googleSubject) {
        UserId userId = requireUserId(googleSubject);
        return new BrowserLanguageSettingsDto(
                userLanguageSettingsService.getCurrent(userId),
                languagePairSupport.supportedPairs()
        );
    }

    @Override
    @Transactional
    public BrowserLanguageSettingsDto updateByGoogleSubject(
            String googleSubject,
            String sourceLanguage,
            String definitionLanguage
    ) {
        UserId userId = requireUserId(googleSubject);
        LanguagePairUpdateResultDto result = userLanguageSettingsService.update(
                userId,
                sourceLanguage,
                definitionLanguage
        );
        if (result.outcome() == LanguagePairUpdateResultDto.Outcome.UNSUPPORTED) {
            throw new UnsupportedBrowserLanguagePairException();
        }
        return new BrowserLanguageSettingsDto(result.languagePair(), result.supportedPairs());
    }

    private UserId requireUserId(String googleSubject) {
        return userIdentityService
                .findUserId(UserIdentityEntity.Provider.GOOGLE, googleSubject)
                .orElseThrow(() -> new IllegalStateException("Authenticated Google identity is not linked"));
    }
}
