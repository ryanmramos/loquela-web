package com.example.whatsanki.service.configuration;

import com.example.whatsanki.dto.ApiProblemDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.security.AuthenticationRateLimitFilter;
import com.example.whatsanki.service.security.CsrfCookieFilter;
import com.example.whatsanki.service.security.SpaCsrfTokenRequestHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class BrowserSecurityConfiguration {

    private final WhatsAnkiConfigurationDto configuration;
    private final ObjectMapper objectMapper;
    private final UserIdentityService userIdentityService;

    @Bean
    SecurityFilterChain applicationSecurityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .secure(configuration.getSecurity().isSecureCookies())
                .sameSite("Lax"));

        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/webhooks/**", "/actuator/health/**").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico", "/assets/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers("/webhooks/**"))
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeProblem(request, response, 401, "Authentication required",
                                        "Sign in with Google to access this resource."))
                        .accessDeniedHandler((request, response, exception) ->
                                writeProblem(request, response, 403, "Access denied",
                                        "The authenticated session cannot perform this operation.")))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/session/logout")
                        .deleteCookies("LOQUELA_SESSION", "XSRF-TOKEN")
                        .clearAuthentication(true)
                        .invalidateHttpSession(true)
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
                        )))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .addFilterBefore(
                        new AuthenticationRateLimitFilter(
                                objectMapper,
                                configuration.getSecurity().getAuthRequestsPerMinute(),
                                configuration.getSecurity().getLinkRequestsPerMinute()
                        ),
                        UsernamePasswordAuthenticationFilter.class
                );

        if (configuration.getSecurity().isGoogleOauthEnabled()) {
            http.oauth2Login(oauth -> oauth
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService()))
                    .defaultSuccessUrl("/app", true)
                    .failureUrl("/login?oauthError=true"));
        }
        return http.build();
    }

    @Bean
    OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        OidcUserService delegate = new OidcUserService();
        return request -> {
            OidcUser oidcUser = delegate.loadUser(request);
            String verifiedEmail = Boolean.TRUE.equals(oidcUser.getEmailVerified())
                    ? oidcUser.getEmail() : null;
            userIdentityService.resolveOrCreateGoogle(
                    oidcUser.getSubject(),
                    verifiedEmail,
                    oidcUser.getFullName()
            );
            return oidcUser;
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!configuration.getSecurity().getAllowedOrigins().isEmpty()) {
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(List.copyOf(configuration.getSecurity().getAllowedOrigins()));
            cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            cors.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Correlation-ID", "Idempotency-Key"));
            cors.setExposedHeaders(List.of("X-Correlation-ID"));
            cors.setAllowCredentials(true);
            source.registerCorsConfiguration("/api/**", cors);
        }
        return source;
    }

    private void writeProblem(
            HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            int status,
            String title,
            String detail
    ) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiProblemDto(
                        URI.create("https://loquela.app/problems/" + status),
                        title,
                        status,
                        detail,
                        request.getRequestURI(),
                        MDC.get("correlationId"),
                        List.of()
                )
        );
    }
}
