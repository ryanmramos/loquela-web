package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.domain.WhatsAppAddress;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.UserIdentityRepository;
import com.example.whatsanki.service.IdentityProtectionService;
import com.example.whatsanki.service.card.DefaultDeckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserIdentityServiceImplTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private UserIdentityRepository userIdentityRepository;
    @Mock private IdentityProtectionService identityProtectionService;
    @Mock private DefaultDeckService defaultDeckService;

    private WhatsAnkiConfigurationDto configuration;
    private UserIdentityServiceImpl service;

    @BeforeEach
    void setUp() {
        configuration = new WhatsAnkiConfigurationDto();
        service = new UserIdentityServiceImpl(
                appUserRepository,
                userIdentityRepository,
                identityProtectionService,
                defaultDeckService,
                configuration
        );
    }

    @Test
    void normalizesAndResolvesExistingWhatsAppIdentity() {
        byte[] hash = {1, 2, 3};
        UUID existingId = UUID.randomUUID();
        UserIdentityEntity identity = identity(existingId, UserIdentityEntity.Provider.WHATSAPP, hash);
        WhatsAppAddress address = new WhatsAppAddress("whatsapp:+15550000001");
        when(identityProtectionService.hash(address)).thenReturn(hash);
        when(userIdentityRepository.findByProviderAndProviderSubjectHash(
                UserIdentityEntity.Provider.WHATSAPP, hash
        )).thenReturn(Optional.of(identity));

        UserId result = service.resolveOrCreate("  WHATSAPP:+15550000001  ");

        assertThat(result.value()).isEqualTo(existingId);
        verify(identityProtectionService, never()).encrypt(any());
        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    void createsWhatsAppOnlyUserWithConfiguredLanguageDefaults() {
        configuration.getLanguages().setDefaultSource("ES-latn-us");
        configuration.getLanguages().setDefaultDefinition("EN-us");
        byte[] hash = {4, 5, 6};
        byte[] ciphertext = {7, 8, 9};
        WhatsAppAddress address = new WhatsAppAddress("whatsapp:+15550000001");
        when(identityProtectionService.hash(address)).thenReturn(hash);
        when(identityProtectionService.encrypt(address)).thenReturn(ciphertext);
        when(userIdentityRepository.findByProviderAndProviderSubjectHash(
                UserIdentityEntity.Provider.WHATSAPP, hash
        )).thenReturn(Optional.empty());
        when(appUserRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userIdentityRepository.insertIfAbsent(
                any(), any(), eq("WHATSAPP"), eq(hash), eq(ciphertext),
                eq(null), eq(null), any(Instant.class)
        )).thenReturn(1);

        UserId result = service.resolveOrCreate(address.value());

        ArgumentCaptor<AppUserEntity> user = ArgumentCaptor.forClass(AppUserEntity.class);
        verify(appUserRepository).saveAndFlush(user.capture());
        assertThat(user.getValue().getId()).isEqualTo(result.value());
        assertThat(user.getValue().getSourceLanguage()).isEqualTo("es-Latn-US");
        assertThat(user.getValue().getDefinitionLanguage()).isEqualTo("en-US");
        verify(defaultDeckService, never()).resolveOrCreate(any(), any());
    }

    @Test
    void createsCompleteGoogleOnlyUserAndDefaultDeck() {
        byte[] hash = {10, 11, 12};
        when(identityProtectionService.hashSubject("google-subject")).thenReturn(hash);
        when(userIdentityRepository.findByProviderAndProviderSubjectHash(
                UserIdentityEntity.Provider.GOOGLE, hash
        )).thenReturn(Optional.empty());
        when(appUserRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userIdentityRepository.insertIfAbsent(
                any(), any(), eq("GOOGLE"), eq(hash), eq(null),
                eq("learner@example.com"), eq("Learner"), any(Instant.class)
        )).thenReturn(1);

        UserId result = service.resolveOrCreateGoogle(
                "google-subject", "learner@example.com", "Learner"
        );

        verify(defaultDeckService).resolveOrCreate(eq(result), any(Instant.class));
        verify(identityProtectionService, never()).encryptSubject(any());
        verify(userIdentityRepository).insertIfAbsent(
                any(), eq(result.value()), eq("GOOGLE"), eq(hash), eq(null),
                eq("learner@example.com"), eq("Learner"), any(Instant.class)
        );
    }

    @Test
    void returnsWinningUserAndDeletesEmptyCandidateAfterConcurrentConflict() {
        byte[] hash = {13, 14, 15};
        UUID winnerId = UUID.randomUUID();
        UserIdentityEntity winner = identity(winnerId, UserIdentityEntity.Provider.GOOGLE, hash);
        when(identityProtectionService.hashSubject("google-subject")).thenReturn(hash);
        when(userIdentityRepository.findByProviderAndProviderSubjectHash(
                UserIdentityEntity.Provider.GOOGLE, hash
        )).thenReturn(Optional.empty(), Optional.of(winner));
        when(appUserRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userIdentityRepository.insertIfAbsent(
                any(), any(), eq("GOOGLE"), eq(hash), eq(null),
                eq(null), eq(null), any(Instant.class)
        )).thenReturn(0);

        UserId result = service.resolveOrCreateGoogle("google-subject", null, null);

        assertThat(result.value()).isEqualTo(winnerId);
        verify(appUserRepository).deleteById(any());
        verify(appUserRepository).flush();
        verify(defaultDeckService, never()).resolveOrCreate(any(), any());
    }

    private UserIdentityEntity identity(
            UUID userId,
            UserIdentityEntity.Provider provider,
            byte[] hash
    ) {
        UserIdentityEntity identity = new UserIdentityEntity();
        identity.setId(UUID.randomUUID());
        identity.setUserId(userId);
        identity.setProvider(provider);
        identity.setProviderSubjectHash(hash);
        return identity;
    }
}
