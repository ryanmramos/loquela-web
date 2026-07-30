package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.BrowserLanguageSettingsDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LanguagePairUpdateResultDto;
import com.example.whatsanki.service.LanguagePairSupport;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.UserLanguageSettingsService;
import com.example.whatsanki.service.exception.UnsupportedBrowserLanguagePairException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrowserLanguageSettingsServiceImplTest {

    @Mock
    private UserIdentityService userIdentityService;
    @Mock
    private UserLanguageSettingsService userLanguageSettingsService;
    @Mock
    private LanguagePairSupport languagePairSupport;

    private BrowserLanguageSettingsServiceImpl service;
    private UserId userId;

    @BeforeEach
    void setUp() {
        service = new BrowserLanguageSettingsServiceImpl(
                userIdentityService,
                userLanguageSettingsService,
                languagePairSupport
        );
        userId = new UserId(UUID.randomUUID());
        when(userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, "google-subject"))
                .thenReturn(Optional.of(userId));
    }

    @Test
    void returnsCurrentAndSupportedLanguagePairs() {
        LanguagePairDto current = new LanguagePairDto("es", "en");
        List<LanguagePairDto> supported = List.of(current, new LanguagePairDto("en", "es"));
        when(userLanguageSettingsService.getCurrent(userId)).thenReturn(current);
        when(languagePairSupport.supportedPairs()).thenReturn(supported);

        BrowserLanguageSettingsDto result = service.getByGoogleSubject("google-subject");

        assertThat(result.languagePair()).isEqualTo(current);
        assertThat(result.supportedPairs()).containsExactlyElementsOf(supported);
    }

    @Test
    void updatesTheAuthenticatedUsersWholeLanguagePair() {
        LanguagePairDto requested = new LanguagePairDto("en", "es");
        List<LanguagePairDto> supported = List.of(new LanguagePairDto("es", "en"), requested);
        when(userLanguageSettingsService.update(userId, "en", "es"))
                .thenReturn(new LanguagePairUpdateResultDto(
                        LanguagePairUpdateResultDto.Outcome.UPDATED,
                        requested,
                        supported
                ));

        BrowserLanguageSettingsDto result = service.updateByGoogleSubject(
                "google-subject",
                "en",
                "es"
        );

        assertThat(result.languagePair()).isEqualTo(requested);
        verify(userLanguageSettingsService).update(userId, "en", "es");
    }

    @Test
    void rejectsAnUnsupportedLanguagePair() {
        LanguagePairDto requested = new LanguagePairDto("fr", "en");
        when(userLanguageSettingsService.update(userId, "fr", "en"))
                .thenReturn(new LanguagePairUpdateResultDto(
                        LanguagePairUpdateResultDto.Outcome.UNSUPPORTED,
                        requested,
                        List.of(new LanguagePairDto("es", "en"))
                ));

        assertThatThrownBy(() -> service.updateByGoogleSubject("google-subject", "fr", "en"))
                .isInstanceOf(UnsupportedBrowserLanguagePairException.class);
    }
}
