package com.example.whatsanki.service.security;

import com.example.whatsanki.service.demo.DemoWorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReauthenticationAuthorizationRequestResolverTest {

    private final DemoWorkspaceService demoWorkspaceService = mock(DemoWorkspaceService.class);
    private final ReauthenticationAuthorizationRequestResolver resolver =
            new ReauthenticationAuthorizationRequestResolver(repository(), demoWorkspaceService);

    @Test
    void forwardsLoginPromptForExplicitReauthenticationRequest() {
        MockHttpServletRequest request = authorizationRequest();
        request.setParameter("prompt", "login");

        OAuth2AuthorizationRequest result = resolver.resolve(request);

        assertThat(result).isNotNull();
        assertThat(result.getAdditionalParameters()).containsEntry("prompt", "login");
        assertThat(result.getAuthorizationRequestUri()).contains("prompt=login");
        verify(demoWorkspaceService).cleanupForSignIn(request.getSession());
    }

    @Test
    void doesNotForwardOtherPromptValues() {
        MockHttpServletRequest request = authorizationRequest();
        request.setParameter("prompt", "consent");

        OAuth2AuthorizationRequest result = resolver.resolve(request);

        assertThat(result).isNotNull();
        assertThat(result.getAdditionalParameters()).doesNotContainKey("prompt");
        verify(demoWorkspaceService).cleanupForSignIn(request.getSession());
    }

    private MockHttpServletRequest authorizationRequest() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        request.setServletPath("/oauth2/authorization/google");
        request.setScheme("https");
        request.setServerName("loquela.example");
        request.setServerPort(443);
        request.getSession();
        return request;
    }

    private ClientRegistrationRepository repository() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id")
                .clientSecret("client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
        return registrationId -> "google".equals(registrationId) ? registration : null;
    }
}
