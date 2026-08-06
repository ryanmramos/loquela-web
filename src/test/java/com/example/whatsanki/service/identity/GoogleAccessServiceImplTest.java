package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.AccessEntryEntity;
import com.example.whatsanki.repository.AccessEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleAccessServiceImplTest {
    private final AccessEntryRepository repository = mock(AccessEntryRepository.class);
    private final GoogleAccessServiceImpl service = new GoogleAccessServiceImpl(repository);

    @Test
    void normalizesOnlyCaseAndSurroundingWhitespace() {
        assertThat(service.normalizeEmail("  User+Study@Example.COM "))
                .isEqualTo("user+study@example.com");
    }

    @Test
    void admitsOnlyAnActiveExactEntry() {
        AccessEntryEntity entry = new AccessEntryEntity();
        entry.setNormalizedEmail("learner@example.com");
        entry.setRole(AccessEntryEntity.Role.USER);
        entry.setStatus(AccessEntryEntity.Status.ACTIVE);
        when(repository.findByNormalizedEmail("learner@example.com"))
                .thenReturn(Optional.of(entry));

        assertThat(service.requireActiveAccess("LEARNER@example.com")).isSameAs(entry);
        assertThatThrownBy(() -> service.requireActiveAccess("learner+tag@example.com"))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void failsClosedForMissingUnverifiedOrRevokedEmail() {
        AccessEntryEntity revoked = new AccessEntryEntity();
        revoked.setStatus(AccessEntryEntity.Status.REVOKED);
        when(repository.findByNormalizedEmail("revoked@example.com"))
                .thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.requireActiveAccess(null))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> service.requireActiveAccess("revoked@example.com"))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }
}

