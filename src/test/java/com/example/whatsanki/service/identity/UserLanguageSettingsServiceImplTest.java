package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LanguagePairUpdateResultDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.service.LanguagePairSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLanguageSettingsServiceImplTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private LanguagePairSupport languagePairSupport;

    @InjectMocks
    private UserLanguageSettingsServiceImpl service;

    @Test
    void returnsCanonicalLanguagePairForActiveUser() {
        UserId userId = UserId.random();
        AppUserEntity user = user("ES-latn-us", "en-us");
        when(appUserRepository.findByIdAndStatus(userId.value(), AppUserEntity.Status.ACTIVE))
                .thenReturn(Optional.of(user));

        var result = service.getCurrent(userId);

        assertThat(result.sourceLanguage()).isEqualTo("es-Latn-US");
        assertThat(result.definitionLanguage()).isEqualTo("en-US");
        verify(appUserRepository).findByIdAndStatus(userId.value(), AppUserEntity.Status.ACTIVE);
    }

    @Test
    void failsWhenActiveUserCannotBeResolved() {
        UserId userId = UserId.random();
        when(appUserRepository.findByIdAndStatus(userId.value(), AppUserEntity.Status.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrent(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Active user language settings not found");
    }

    @Test
    void rejectsMissingUserIdBeforeRepositoryAccess() {
        assertThatNullPointerException().isThrownBy(() -> service.getCurrent(null));
    }

    @Test
    void atomicallyUpdatesBothLanguagesWhenCanonicalPairIsSupported() {
        UserId userId = UserId.random();
        AppUserEntity user = user("es", "en");
        LanguagePairDto requested = new LanguagePairDto("EN-us", "ES");
        when(languagePairSupport.supports(requested)).thenReturn(true);
        when(languagePairSupport.supportedPairs()).thenReturn(List.of(requested));
        when(appUserRepository.findByIdAndStatus(userId.value(), AppUserEntity.Status.ACTIVE))
                .thenReturn(Optional.of(user));

        var result = service.update(userId, "EN-us", "ES");

        assertThat(result.outcome()).isEqualTo(LanguagePairUpdateResultDto.Outcome.UPDATED);
        assertThat(result.languagePair()).isEqualTo(new LanguagePairDto("en-US", "es"));
        assertThat(user.getSourceLanguage()).isEqualTo("en-US");
        assertThat(user.getDefinitionLanguage()).isEqualTo("es");
        verify(appUserRepository).save(user);
    }

    @Test
    void returnsUnchangedWithoutWritingWhenPairIsAlreadyActive() {
        UserId userId = UserId.random();
        AppUserEntity user = user("es", "en");
        LanguagePairDto requested = new LanguagePairDto("es", "en");
        when(languagePairSupport.supports(requested)).thenReturn(true);
        when(languagePairSupport.supportedPairs()).thenReturn(List.of(requested));
        when(appUserRepository.findByIdAndStatus(userId.value(), AppUserEntity.Status.ACTIVE))
                .thenReturn(Optional.of(user));

        var result = service.update(userId, "ES", "EN");

        assertThat(result.outcome()).isEqualTo(LanguagePairUpdateResultDto.Outcome.UNCHANGED);
        verify(appUserRepository, never()).save(user);
    }

    @Test
    void rejectsUnsupportedPairBeforeLoadingOrChangingUser() {
        UserId userId = UserId.random();
        LanguagePairDto requested = new LanguagePairDto("fr", "de");
        List<LanguagePairDto> supported = List.of(new LanguagePairDto("es", "en"));
        when(languagePairSupport.supports(requested)).thenReturn(false);
        when(languagePairSupport.supportedPairs()).thenReturn(supported);

        var result = service.update(userId, "fr", "de");

        assertThat(result.outcome()).isEqualTo(LanguagePairUpdateResultDto.Outcome.UNSUPPORTED);
        assertThat(result.supportedPairs()).containsExactlyElementsOf(supported);
        verify(appUserRepository, never())
                .findByIdAndStatus(userId.value(), AppUserEntity.Status.ACTIVE);
        verify(appUserRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMalformedTagBeforeCheckingCapabilitiesOrLoadingUser() {
        UserId userId = UserId.random();

        assertThatThrownBy(() -> service.update(userId, "invalid_tag", "en"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid BCP 47");
        verify(languagePairSupport, never()).supports(org.mockito.ArgumentMatchers.any());
        verify(appUserRepository, never())
                .findByIdAndStatus(userId.value(), AppUserEntity.Status.ACTIVE);
    }

    private AppUserEntity user(String sourceLanguage, String definitionLanguage) {
        AppUserEntity user = new AppUserEntity();
        user.setSourceLanguage(sourceLanguage);
        user.setDefinitionLanguage(definitionLanguage);
        user.setStatus(AppUserEntity.Status.ACTIVE);
        return user;
    }
}
