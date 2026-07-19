package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.service.UserLanguageSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserLanguageSettingsServiceImpl implements UserLanguageSettingsService {

    private final AppUserRepository appUserRepository;

    @Override
    @Transactional(readOnly = true)
    public LanguagePairDto getCurrent(UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        // Restrict settings reads to the active authenticated user. A missing row is an
        // invariant violation because webhook identity resolution precedes dispatch.
        AppUserEntity user = appUserRepository
                .findByIdAndStatus(userId.value(), AppUserEntity.Status.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Active user language settings not found"));
        LanguagePairDto languagePair = new LanguagePairDto(
                user.getSourceLanguage(),
                user.getDefinitionLanguage()
        );
        log.info(
                "Resolved user language settings userId={} sourceLanguage={} definitionLanguage={}",
                userId.value(),
                languagePair.sourceLanguage(),
                languagePair.definitionLanguage()
        );
        return languagePair;
    }
}
