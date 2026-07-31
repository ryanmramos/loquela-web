package com.example.whatsanki.controller;

import com.example.whatsanki.dto.BrowserAccountDeletionDto;
import com.example.whatsanki.dto.BrowserAccountDeletionRequestDto;
import com.example.whatsanki.service.BrowserPrivacyService;
import com.example.whatsanki.service.exception.BrowserPrivacyException;
import com.example.whatsanki.service.security.BrowserAuthenticationSuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrowserPrivacyControllerTest {
    private final BrowserPrivacyService privacyService = mock(BrowserPrivacyService.class);
    private final BrowserPrivacyController controller =
            new BrowserPrivacyController(privacyService);

    @Test
    void rejectsDeletionWhenGoogleAuthenticationIsNotRecent() {
        MockHttpSession session = new MockHttpSession();

        assertThatThrownBy(() -> controller.create(
                authentication(), session, new BrowserAccountDeletionRequestDto("DELETE MY ACCOUNT")
        )).isInstanceOfSatisfying(BrowserPrivacyException.class, exception ->
                assertThat(exception.problemCode()).isEqualTo("RECENT_AUTHENTICATION_REQUIRED"));

        verify(privacyService, never()).deleteAccount(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recentlyReauthenticatedUserQueuesOnlyTheirOwnDeletion() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                BrowserAuthenticationSuccessHandler.RECENT_AUTHENTICATION_AT,
                Instant.now().toEpochMilli()
        );
        when(privacyService.deleteAccount("google-subject"))
                .thenReturn(new BrowserAccountDeletionDto(
                        "DELETING", "job-reference", Instant.now()
                ));

        var response = controller.create(
                authentication(), session, new BrowserAccountDeletionRequestDto("DELETE MY ACCOUNT")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("DELETING");
        verify(privacyService).deleteAccount("google-subject");
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(
                "google-subject", null, List.of()
        );
    }
}
