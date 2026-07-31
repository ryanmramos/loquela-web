package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.UpdateBrowserSettingsRequestDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.UserIdentityRepository;
import com.example.whatsanki.service.IdentityProtectionService;
import com.example.whatsanki.service.LanguagePairSupport;
import com.example.whatsanki.service.exception.UnsupportedBrowserLanguagePairException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrowserSettingsServiceImplTest {
    @Mock private UserIdentityRepository identityRepository;
    @Mock private AppUserRepository userRepository;
    @Mock private IdentityProtectionService protectionService;
    @Mock private LanguagePairSupport languagePairSupport;

    private BrowserSettingsServiceImpl service;
    private UserIdentityEntity identity;
    private AppUserEntity user;

    @BeforeEach
    void setUp() {
        service = new BrowserSettingsServiceImpl(
                identityRepository, userRepository, protectionService, languagePairSupport
        );
        identity = new UserIdentityEntity();
        identity.setUserId(UUID.randomUUID());
        identity.setDisplayName("Google Name");
        identity.setDisplayEmail("learner@example.com");
        identity.setCreatedAt(Instant.parse("2026-07-18T00:00:00Z"));
        user = new AppUserEntity();
        user.setId(identity.getUserId());
        user.setStatus(AppUserEntity.Status.ACTIVE);
        user.setSourceLanguage("es");
        user.setDefinitionLanguage("en");
        user.setAutomaticCaptionFallback(true);
        user.setExportDeckPrefix("Loquela — ");
        user.setExportIncludeProvenance(true);
        when(protectionService.hashSubject("google")).thenReturn(new byte[]{1});
        when(identityRepository.findByProviderAndProviderSubjectHash(
                org.mockito.ArgumentMatchers.eq(UserIdentityEntity.Provider.GOOGLE),
                any(byte[].class)
        )).thenReturn(Optional.of(identity));
        when(userRepository.findById(identity.getUserId())).thenReturn(Optional.of(user));
    }

    @Test
    void returnsOnlySafeGoogleMetadataAndPreferences() {
        when(languagePairSupport.supportedPairs()).thenReturn(List.of(new LanguagePairDto("es", "en")));

        var settings = service.get("google");

        assertThat(settings.profile().displayName()).isEqualTo("Google Name");
        assertThat(settings.profile().email()).isEqualTo("learner@example.com");
        assertThat(settings.automaticCaptionFallback()).isTrue();
        assertThat(settings.linkedAccounts().google()).isTrue();
    }

    @Test
    void updatesTheWholeFutureWorkPreferenceSet() {
        LanguagePairDto pair = new LanguagePairDto("en", "es");
        when(languagePairSupport.supports(pair)).thenReturn(true);
        when(languagePairSupport.supportedPairs()).thenReturn(List.of(pair));
        when(userRepository.save(user)).thenReturn(user);

        var result = service.update("google", new UpdateBrowserSettingsRequestDto(
                "en", "es", false, "Mine — ", false
        ));

        assertThat(result.languagePair()).isEqualTo(pair);
        assertThat(user.isAutomaticCaptionFallback()).isFalse();
        assertThat(user.getExportDeckPrefix()).isEqualTo("Mine —");
        assertThat(user.isExportIncludeProvenance()).isFalse();
    }

    @Test
    void rejectsUnsupportedPairsWithoutPersistingAnyPreference() {
        when(languagePairSupport.supports(any())).thenReturn(false);

        assertThatThrownBy(() -> service.update("google", new UpdateBrowserSettingsRequestDto(
                "fr", "en", false, "", false
        ))).isInstanceOf(UnsupportedBrowserLanguagePairException.class);

        verify(userRepository, never()).save(any());
        assertThat(user.getSourceLanguage()).isEqualTo("es");
    }
}
