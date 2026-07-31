package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.UserIdentityRepository;
import com.example.whatsanki.service.IdentityProtectionService;
import com.example.whatsanki.service.exception.BrowserAccountUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrowserSessionServiceImplTest {
    @Test
    void completedDeletionTurnsAStaleAuthenticatedSessionIntoAccountDeleted() {
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        IdentityProtectionService protection = mock(IdentityProtectionService.class);
        when(protection.hashSubject("google")).thenReturn(new byte[]{1});
        when(identities.findByProviderAndProviderSubjectHash(
                org.mockito.ArgumentMatchers.eq(UserIdentityEntity.Provider.GOOGLE),
                any(byte[].class)
        )).thenReturn(Optional.empty());
        var service = new BrowserSessionServiceImpl(
                identities, mock(AppUserRepository.class), protection
        );

        assertThatThrownBy(() -> service.getByGoogleSubject("google"))
                .isInstanceOfSatisfying(BrowserAccountUnavailableException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.problemCode())
                                .isEqualTo("ACCOUNT_DELETED"));
    }
}
