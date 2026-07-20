package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.WhatsAppAddress;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.service.IdentityProtectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private IdentityProtectionService identityProtectionService;

    private WhatsAnkiConfigurationDto configuration;

    @InjectMocks
    private UserIdentityServiceImpl service;

    @BeforeEach
    void setUp() {
        configuration = new WhatsAnkiConfigurationDto();
        service = new UserIdentityServiceImpl(appUserRepository, identityProtectionService, configuration);
    }

    @Test
    void normalizesAndResolvesExistingUserWithoutCreatingAnother() {
        byte[] hash = {1, 2, 3};
        UUID existingId = UUID.randomUUID();
        AppUserEntity existingUser = new AppUserEntity();
        existingUser.setId(existingId);
        when(identityProtectionService.hash(new WhatsAppAddress("whatsapp:+15550000001"))).thenReturn(hash);
        when(appUserRepository.findByWhatsappAddressHash(hash)).thenReturn(Optional.of(existingUser));

        var result = service.resolveOrCreate("  WHATSAPP:+15550000001  ");

        assertThat(result.value()).isEqualTo(existingId);
        verify(identityProtectionService, never()).encrypt(any());
        verify(appUserRepository, never()).insertIfAbsent(
                any(UUID.class), any(byte[].class), any(byte[].class),
                any(String.class), any(String.class), any(Instant.class)
        );
    }

    @Test
    void createsNewUserWithConfiguredLanguageDefaults() {
        configuration.getLanguages().setDefaultSource("ES-latn-us");
        configuration.getLanguages().setDefaultDefinition("EN-us");
        byte[] hash = {4, 5, 6};
        byte[] ciphertext = {7, 8, 9};
        WhatsAppAddress address = new WhatsAppAddress("whatsapp:+15550000001");
        when(identityProtectionService.hash(address)).thenReturn(hash);
        when(identityProtectionService.encrypt(address)).thenReturn(ciphertext);
        when(appUserRepository.findByWhatsappAddressHash(hash)).thenReturn(Optional.empty());
        when(appUserRepository.insertIfAbsent(
                any(UUID.class), eq(hash), eq(ciphertext), eq("es-Latn-US"), eq("en-US"), any(Instant.class)
        )).thenReturn(1);

        var result = service.resolveOrCreate(address.value());

        assertThat(result.value()).isNotNull();
        verify(appUserRepository).insertIfAbsent(
                eq(result.value()), eq(hash), eq(ciphertext), eq("es-Latn-US"), eq("en-US"), any(Instant.class)
        );
    }

    @Test
    void returnsWinningUserWhenConcurrentCreationLosesConflict() {
        byte[] hash = {10, 11, 12};
        byte[] ciphertext = {13, 14, 15};
        UUID winningId = UUID.randomUUID();
        AppUserEntity winningUser = new AppUserEntity();
        winningUser.setId(winningId);
        WhatsAppAddress address = new WhatsAppAddress("whatsapp:+15550000001");
        when(identityProtectionService.hash(address)).thenReturn(hash);
        when(identityProtectionService.encrypt(address)).thenReturn(ciphertext);
        when(appUserRepository.findByWhatsappAddressHash(hash))
                .thenReturn(Optional.empty(), Optional.of(winningUser));
        when(appUserRepository.insertIfAbsent(
                any(UUID.class), eq(hash), eq(ciphertext), eq("es"), eq("en"), any(Instant.class)
        )).thenReturn(0);

        var result = service.resolveOrCreate(address.value());

        assertThat(result.value()).isEqualTo(winningId);
        verify(appUserRepository).insertIfAbsent(
                any(UUID.class), eq(hash), eq(ciphertext), eq("es"), eq("en"), any(Instant.class)
        );
    }
}
