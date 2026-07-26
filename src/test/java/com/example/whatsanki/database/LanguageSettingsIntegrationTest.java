package com.example.whatsanki.database;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.LanguagePairUpdateResultDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.service.UserLanguageSettingsService;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

class LanguageSettingsIntegrationTest extends PostgreSqlIntegrationTest {

    private final AppUserRepository appUserRepository;
    private final UserLanguageSettingsService userLanguageSettingsService;

    LanguageSettingsIntegrationTest(
            AppUserRepository appUserRepository,
            UserLanguageSettingsService userLanguageSettingsService
    ) {
        this.appUserRepository = appUserRepository;
        this.userLanguageSettingsService = userLanguageSettingsService;
    }

    @Test
    void persistsSupportedPairTogetherWithoutChangingAnotherUser() {
        AppUserEntity user = appUserRepository.saveAndFlush(user("language-owner"));
        AppUserEntity otherUser = appUserRepository.saveAndFlush(user("other-language-owner"));

        var result = userLanguageSettingsService.update(new UserId(user.getId()), "EN", "ES");

        assertThat(result.outcome()).isEqualTo(LanguagePairUpdateResultDto.Outcome.UPDATED);
        AppUserEntity reloaded = appUserRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getSourceLanguage()).isEqualTo("en");
        assertThat(reloaded.getDefinitionLanguage()).isEqualTo("es");
        AppUserEntity otherReloaded = appUserRepository.findById(otherUser.getId()).orElseThrow();
        assertThat(otherReloaded.getSourceLanguage()).isEqualTo("es");
        assertThat(otherReloaded.getDefinitionLanguage()).isEqualTo("en");
    }

    @Test
    void unsupportedPairLeavesBothPersistedValuesUnchanged() {
        AppUserEntity user = appUserRepository.saveAndFlush(user("unsupported-language-owner"));

        var result = userLanguageSettingsService.update(new UserId(user.getId()), "fr", "de");

        assertThat(result.outcome()).isEqualTo(LanguagePairUpdateResultDto.Outcome.UNSUPPORTED);
        AppUserEntity reloaded = appUserRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getSourceLanguage()).isEqualTo("es");
        assertThat(reloaded.getDefinitionLanguage()).isEqualTo("en");
    }

    @Test
    void persistsConfiguredMonolingualPairForAdvancedLearner() {
        AppUserEntity user = appUserRepository.saveAndFlush(user("monolingual-language-owner"));

        var result = userLanguageSettingsService.update(new UserId(user.getId()), "ES", "es");

        assertThat(result.outcome()).isEqualTo(LanguagePairUpdateResultDto.Outcome.UPDATED);
        AppUserEntity reloaded = appUserRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getSourceLanguage()).isEqualTo("es");
        assertThat(reloaded.getDefinitionLanguage()).isEqualTo("es");
    }

    private AppUserEntity user(String identity) {
        AppUserEntity user = new AppUserEntity();
        user.setSourceLanguage("es");
        user.setDefinitionLanguage("en");
        user.setStatus(AppUserEntity.Status.ACTIVE);
        return user;
    }
}
