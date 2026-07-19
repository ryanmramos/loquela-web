package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LanguagePairUpdateResultDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.service.LanguagePairSupport;
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
    private final LanguagePairSupport languagePairSupport;

    @Override
    @Transactional(readOnly = true)
    public LanguagePairDto getCurrent(UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        // Restrict settings reads to the active authenticated user. A missing row is an
        // invariant violation because webhook identity resolution precedes dispatch.
        LanguagePairDto languagePair = pairOf(activeUser(userId));
        log.info(
                "Resolved user language settings userId={} sourceLanguage={} definitionLanguage={}",
                userId.value(),
                languagePair.sourceLanguage(),
                languagePair.definitionLanguage()
        );
        return languagePair;
    }

    @Override
    @Transactional
    public LanguagePairUpdateResultDto update(
            UserId userId,
            String sourceLanguage,
            String definitionLanguage
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        LanguagePairDto requestedPair = new LanguagePairDto(sourceLanguage, definitionLanguage);
        if (!languagePairSupport.supports(requestedPair)) {
            // Reject before loading or mutating the user. This makes it impossible for
            // an unsupported request to partially update one side of the language pair.
            log.info(
                    "Rejected unsupported language pair userId={} sourceLanguage={} definitionLanguage={}",
                    userId.value(),
                    requestedPair.sourceLanguage(),
                    requestedPair.definitionLanguage()
            );
            return new LanguagePairUpdateResultDto(
                    LanguagePairUpdateResultDto.Outcome.UNSUPPORTED,
                    requestedPair,
                    languagePairSupport.supportedPairs()
            );
        }

        AppUserEntity user = activeUser(userId);
        LanguagePairDto currentPair = pairOf(user);
        if (currentPair.equals(requestedPair)) {
            log.info(
                    "Language pair was already configured userId={} sourceLanguage={} definitionLanguage={}",
                    userId.value(),
                    requestedPair.sourceLanguage(),
                    requestedPair.definitionLanguage()
            );
            return new LanguagePairUpdateResultDto(
                    LanguagePairUpdateResultDto.Outcome.UNCHANGED,
                    requestedPair,
                    languagePairSupport.supportedPairs()
            );
        }

        // Both fields are changed on one managed entity and flushed by one transaction,
        // so readers can observe either the old pair or the new pair, never a mixed pair.
        user.setSourceLanguage(requestedPair.sourceLanguage());
        user.setDefinitionLanguage(requestedPair.definitionLanguage());
        appUserRepository.save(user);
        log.info(
                "Updated user language pair userId={} sourceLanguage={} definitionLanguage={}",
                userId.value(),
                requestedPair.sourceLanguage(),
                requestedPair.definitionLanguage()
        );
        return new LanguagePairUpdateResultDto(
                LanguagePairUpdateResultDto.Outcome.UPDATED,
                requestedPair,
                languagePairSupport.supportedPairs()
        );
    }

    private AppUserEntity activeUser(UserId userId) {
        return appUserRepository
                .findByIdAndStatus(userId.value(), AppUserEntity.Status.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Active user language settings not found"));
    }

    private LanguagePairDto pairOf(AppUserEntity user) {
        return new LanguagePairDto(user.getSourceLanguage(), user.getDefinitionLanguage());
    }
}
