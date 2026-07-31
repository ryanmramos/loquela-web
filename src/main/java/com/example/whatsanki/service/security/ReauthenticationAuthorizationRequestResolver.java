package com.example.whatsanki.service.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Adds the allow-listed Google reauthentication parameter to OAuth requests.
 *
 * <p>Spring Security intentionally does not copy arbitrary query parameters from
 * its authorization endpoint. This resolver forwards only {@code prompt=login},
 * which prevents the account-deletion step-up flow from becoming a silent SSO
 * round trip while avoiding propagation of untrusted parameters.</p>
 */
public final class ReauthenticationAuthorizationRequestResolver
        implements OAuth2AuthorizationRequestResolver {

    private static final String PROMPT_PARAMETER = "prompt";
    private static final String LOGIN_PROMPT = "login";

    private final OAuth2AuthorizationRequestResolver delegate;

    public ReauthenticationAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository
    ) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository);
    }

    @Override
    public @Nullable OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return addReauthenticationPrompt(request, delegate.resolve(request));
    }

    @Override
    public @Nullable OAuth2AuthorizationRequest resolve(
            HttpServletRequest request,
            String clientRegistrationId
    ) {
        return addReauthenticationPrompt(request, delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest addReauthenticationPrompt(
            HttpServletRequest request,
            @Nullable OAuth2AuthorizationRequest authorizationRequest
    ) {
        if (authorizationRequest == null
                || !LOGIN_PROMPT.equals(request.getParameter(PROMPT_PARAMETER))) {
            return authorizationRequest;
        }
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .additionalParameters(parameters -> parameters.put(PROMPT_PARAMETER, LOGIN_PROMPT))
                .build();
    }
}
