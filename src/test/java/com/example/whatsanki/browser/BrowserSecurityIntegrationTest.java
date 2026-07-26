package com.example.whatsanki.browser;

import com.example.whatsanki.controller.RequestCorrelationFilter;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.session.SessionRepository;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

class BrowserSecurityIntegrationTest extends PostgreSqlIntegrationTest {

    private final WebApplicationContext applicationContext;
    private final UserIdentityService userIdentityService;
    private final RequestCorrelationFilter requestCorrelationFilter;
    private final SessionRepository<?> sessionRepository;
    private MockMvc mockMvc;

    BrowserSecurityIntegrationTest(
            WebApplicationContext applicationContext,
            UserIdentityService userIdentityService,
            RequestCorrelationFilter requestCorrelationFilter,
            SessionRepository<?> sessionRepository
    ) {
        this.applicationContext = applicationContext;
        this.userIdentityService = userIdentityService;
        this.requestCorrelationFilter = requestCorrelationFilter;
        this.sessionRepository = sessionRepository;
    }

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = webAppContextSetup(applicationContext)
                .addFilters(requestCorrelationFilter)
                .apply(springSecurity())
                .build();
    }

    @Test
    void unauthenticatedBrowserApiReturnsJsonProblem() throws Exception {
        mockMvc.perform(get("/api/v1/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    @Test
    void googleOnlyUserCallsApiAndUserComesFromAuthenticatedSession() throws Exception {
        String subject = "session-derived-subject";
        UserId userId = userIdentityService.resolveOrCreateGoogle(
                subject,
                "session@example.com",
                "Session User"
        );

        mockMvc.perform(get("/api/v1/session")
                        .with(oidcLogin().idToken(token -> token
                                .subject(subject)
                                .claim("email", "untrusted-request@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.value().toString()))
                .andExpect(jsonPath("$.email").value("session@example.com"))
                .andExpect(jsonPath("$.linkedAccounts.google").value(true))
                .andExpect(jsonPath("$.linkedAccounts.whatsapp").value(false))
                .andExpect(header().string("X-Correlation-ID", not(blankOrNullString())));
    }

    @Test
    void browserSessionsUseJdbcRepository() {
        assertThat(sessionRepository.getClass().getSimpleName())
                .isEqualTo("JdbcIndexedSessionRepository");
    }

    @Test
    void browserMutationWithoutCsrfFailsAndLogoutWithCsrfSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/session/logout").with(oidcLogin()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(post("/api/v1/session/logout")
                        .with(oidcLogin())
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void browserOpenApiContainsOnlyDtoBackedBrowserContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs/browser-v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/session']").exists())
                .andExpect(jsonPath("$.paths['/webhooks/twilio/whatsapp']").doesNotExist())
                .andExpect(jsonPath("$.components.securitySchemes.browserSession").exists())
                .andExpect(jsonPath("$.components.schemas.ApiProblemDto").exists())
                .andExpect(jsonPath("$.components.schemas.AppUserEntity").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UserIdentityEntity").doesNotExist());
    }
}
