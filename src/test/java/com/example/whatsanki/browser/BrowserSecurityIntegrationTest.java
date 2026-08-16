package com.example.whatsanki.browser;

import com.example.whatsanki.controller.ExternalEndpointSecurity;
import com.example.whatsanki.controller.RequestCorrelationFilter;
import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.AccessEntryEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.AccessEntryRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.security.BrowserReturnPath;
import com.example.whatsanki.service.util.SourceReferenceCodec;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.core.env.Environment;
import org.springframework.session.SessionRepository;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private final AppUserRepository appUserRepository;
    private final AccessEntryRepository accessEntryRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final Environment environment;
    private MockMvc mockMvc;

    BrowserSecurityIntegrationTest(
            WebApplicationContext applicationContext,
            UserIdentityService userIdentityService,
            RequestCorrelationFilter requestCorrelationFilter,
            SessionRepository<?> sessionRepository,
            AppUserRepository appUserRepository,
            AccessEntryRepository accessEntryRepository,
            SourceDocumentRepository sourceDocumentRepository,
            Environment environment
    ) {
        this.applicationContext = applicationContext;
        this.userIdentityService = userIdentityService;
        this.requestCorrelationFilter = requestCorrelationFilter;
        this.sessionRepository = sessionRepository;
        this.appUserRepository = appUserRepository;
        this.accessEntryRepository = accessEntryRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.environment = environment;
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
                .andExpect(jsonPath("$.title").value("Authentication required"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
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
                .andExpect(jsonPath("$.accountState").value("ACTIVE"))
                .andExpect(jsonPath("$.linkedAccounts.google").value(true))
                .andExpect(jsonPath("$.linkedAccounts.whatsapp").value(false))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(header().string(
                        "Content-Security-Policy",
                        containsString("img-src 'self' data: https://i.ytimg.com")
                ))
                .andExpect(header().string(
                        "Content-Security-Policy",
                        containsString("frame-src 'self' https://www.youtube-nocookie.com")
                ))
                .andExpect(header().string(
                        "Content-Security-Policy",
                        containsString("require-trusted-types-for 'script'")
                ))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Correlation-ID", not(blankOrNullString())));
    }

    @Test
    void adminApiChecksPersistedAdminAccessOnEveryRequest() throws Exception {
        String adminSubject = "persisted-admin-subject";
        String adminEmail = "persisted-admin@example.com";
        userIdentityService.resolveOrCreateGoogle(adminSubject, adminEmail, "Persisted Admin");
        AccessEntryEntity admin = new AccessEntryEntity();
        admin.setNormalizedEmail(adminEmail);
        admin.setRole(AccessEntryEntity.Role.ADMIN);
        admin.setStatus(AccessEntryEntity.Status.ACTIVE);
        accessEntryRepository.saveAndFlush(admin);

        mockMvc.perform(get("/api/v1/admin/access-entries")
                        .with(oidcLogin().idToken(token -> token.subject(adminSubject))))
                .andExpect(status().isOk());

        admin.setRole(AccessEntryEntity.Role.USER);
        accessEntryRepository.saveAndFlush(admin);

        mockMvc.perform(get("/api/v1/admin/access-entries")
                        .with(oidcLogin().idToken(token -> token.subject(adminSubject))))
                .andExpect(status().isForbidden());
    }

    @Test
    void browserSessionsUseJdbcRepository() {
        assertThat(sessionRepository.getClass().getSimpleName())
                .isEqualTo("JdbcIndexedSessionRepository");
        assertThat(environment.getProperty("server.servlet.session.cookie.http-only"))
                .isEqualTo("true");
        assertThat(environment.getProperty("server.servlet.session.cookie.same-site"))
                .isEqualTo("lax");
    }

    @Test
    void browserMutationWithoutCsrfFailsAndLogoutWithCsrfSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/session/logout").with(oidcLogin()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        mockMvc.perform(post("/api/v1/practice-sessions")
                        .with(oidcLogin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deckReference":"AAAAAAAAAAAAAAAAAAAAAA"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/session/logout")
                        .with(oidcLogin())
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void everyExternallyReachableMutationHasAnExplicitSecurityClassification() {
        RequestMappingHandlerMapping mappings = applicationContext.getBean(
                "requestMappingHandlerMapping",
                RequestMappingHandlerMapping.class
        );

        Map<RequestMappingInfo, org.springframework.web.method.HandlerMethod> externalMutations =
                mappings.getHandlerMethods().entrySet().stream()
                        .filter(entry -> entry.getValue().getBeanType().getPackageName()
                                .equals("com.example.whatsanki.controller"))
                        .filter(entry -> entry.getKey().getMethodsCondition().getMethods().stream()
                                .anyMatch(method -> method != RequestMethod.GET && method != RequestMethod.HEAD
                                        && method != RequestMethod.OPTIONS))
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertThat(externalMutations).hasSize(46);
        externalMutations.forEach((mapping, handler) -> {
            ExternalEndpointSecurity methodClassification = handler.getMethodAnnotation(
                    ExternalEndpointSecurity.class
            );
            ExternalEndpointSecurity classification = methodClassification != null
                    ? methodClassification
                    : handler.getBeanType().getAnnotation(ExternalEndpointSecurity.class);
            assertThat(classification)
                    .as("security classification for %s %s", mapping, handler)
                    .isNotNull();
            mapping.getPatternValues().forEach(path -> {
                if (path.startsWith("/webhooks/")) {
                    assertThat(classification.value())
                            .isEqualTo(ExternalEndpointSecurity.Control.TWILIO_SIGNED_WEBHOOK);
                } else if (path.equals("/api/v1/auth/local-login")) {
                    assertThat(classification.value())
                            .isEqualTo(ExternalEndpointSecurity.Control.DEVELOPMENT_ONLY_CSRF);
                } else if (path.equals("/api/v1/session/logout")) {
                    assertThat(classification.value())
                            .isEqualTo(ExternalEndpointSecurity.Control.BROWSER_SESSION_CSRF);
                } else if (path.startsWith("/api/v1/admin/")) {
                    assertThat(classification.value())
                            .isEqualTo(ExternalEndpointSecurity.Control.BROWSER_SESSION_CSRF_ADMIN);
                } else if (path.startsWith("/api/v1/demo/")) {
                    assertThat(classification.value())
                            .isEqualTo(ExternalEndpointSecurity.Control.ANONYMOUS_DEMO_SESSION_CSRF);
                } else {
                    assertThat(path).startsWith("/api/v1/");
                    assertThat(classification.value())
                            .isEqualTo(ExternalEndpointSecurity.Control.BROWSER_SESSION_CSRF_OWNER_SCOPED);
                }
            });
        });
    }

    @Test
    void dashboardAndQuickCaptureUseAuthenticatedUserAndOneAggregateContract() throws Exception {
        String subject = "dashboard-subject";
        UserId userId = userIdentityService.resolveOrCreateGoogle(
                subject,
                "dashboard@example.com",
                "Dashboard User"
        );

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(oidcLogin().idToken(token -> token.subject(subject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.cards.totalCards").value(0))
                .andExpect(jsonPath("$.sources.totalSources").value(0))
                .andExpect(jsonPath("$.jobs.activeJobs").value(0))
                .andExpect(jsonPath("$.languagePair.sourceLanguage").isNotEmpty())
                .andExpect(jsonPath("$.generatedAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/sources")
                        .with(oidcLogin().idToken(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"No CSRF"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/sources")
                        .with(oidcLogin().idToken(token -> token.subject(subject)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("text"));

        mockMvc.perform(post("/api/v1/sources")
                        .with(oidcLogin().idToken(token -> token.subject(subject)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Una captura rápida desde la web."}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.sourceId").isNotEmpty())
                .andExpect(jsonPath("$.characterCount").value(32));

        assertThat(sourceDocumentRepository.findAllByUserIdOrderByCreatedAtDesc(userId.value()))
                .singleElement()
                .satisfies(source -> {
                    assertThat(source.getMetadata()).containsEntry("origin", "WEBSITE");
                    assertThat(source.getStagedTextCiphertext()).isNotEmpty();
                    assertThat(source.getInboundMessageId()).isNull();
                });
    }

    @Test
    void sourceManagementIsFilteredPaginatedCsrfProtectedAndOwnerScoped() throws Exception {
        String ownerSubject = "source-owner-subject";
        UserId owner = userIdentityService.resolveOrCreateGoogle(
                ownerSubject, "source-owner@example.com", "Source Owner"
        );
        UserId other = userIdentityService.resolveOrCreateGoogle(
                "other-source-subject", "other-source@example.com", "Other Owner"
        );
        SourceDocumentEntity matching = source(owner, "Spanish reader", SourceDocumentEntity.Kind.PDF);
        SourceDocumentEntity hidden = source(other, "Other reader", SourceDocumentEntity.Kind.PDF);
        sourceDocumentRepository.save(matching);
        sourceDocumentRepository.save(hidden);
        String matchingReference = SourceReferenceCodec.encode(new SourceId(matching.getId()));
        String hiddenReference = SourceReferenceCodec.encode(new SourceId(hidden.getId()));

        mockMvc.perform(get("/api/v1/sources")
                        .with(oidcLogin().idToken(token -> token.subject(ownerSubject)))
                        .param("search", "Spanish")
                        .param("kind", "PDF")
                        .param("status", "READY")
                        .param("page", "0")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].reference").value(matchingReference))
                .andExpect(jsonPath("$.items[0].displayName").value("Spanish reader"))
                .andExpect(jsonPath("$.totalItems").value(1));

        // Opening the Sources route sends no optional filters. This guards the PostgreSQL
        // parameter typing used by the default list request.
        mockMvc.perform(get("/api/v1/sources")
                        .with(oidcLogin().idToken(token -> token.subject(ownerSubject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].reference").value(matchingReference))
                .andExpect(jsonPath("$.totalItems").value(1));

        mockMvc.perform(get("/api/v1/sources/" + hiddenReference)
                        .with(oidcLogin().idToken(token -> token.subject(ownerSubject))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SOURCE_NOT_FOUND"));

        mockMvc.perform(patch("/api/v1/sources/" + matchingReference)
                        .with(oidcLogin().idToken(token -> token.subject(ownerSubject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Renamed reader","version":0}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/sources/" + matchingReference)
                        .with(oidcLogin().idToken(token -> token.subject(ownerSubject)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Renamed reader","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Renamed reader"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(delete("/api/v1/sources/" + hiddenReference)
                        .with(oidcLogin().idToken(token -> token.subject(ownerSubject)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SOURCE_NOT_FOUND"));
    }

    @Test
    void googleEntryStoresOnlyValidatedApplicationReturnPaths() throws Exception {
        MockHttpSession validSession = new MockHttpSession();
        mockMvc.perform(get("/api/v1/auth/google")
                        .param("returnTo", "/app/decks?sort=recent")
                        .session(validSession))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/oauth2/authorization/google"));
        assertThat(validSession.getAttribute(BrowserReturnPath.SESSION_ATTRIBUTE))
                .isEqualTo("/app/decks?sort=recent");

        MockHttpSession unsafeSession = new MockHttpSession();
        mockMvc.perform(get("/api/v1/auth/google")
                        .param("returnTo", "https://evil.example/steal")
                        .session(unsafeSession))
                .andExpect(status().isFound());
        assertThat(unsafeSession.getAttribute(BrowserReturnPath.SESSION_ATTRIBUTE))
                .isEqualTo("/app");
    }

    @Test
    void localTestLoginCreatesReusableServerSessionAndLogoutInvalidatesIt() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/v1/auth/local-login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"learner"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("learner@local.loquela.test"))
                .andExpect(jsonPath("$.accountState").value("ACTIVE"))
                .andExpect(jsonPath("$.linkedAccounts.whatsapp").value(false))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn()
                .getRequest()
                .getSession(false);

        mockMvc.perform(get("/api/v1/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Local Learner"));

        mockMvc.perform(post("/api/v1/session/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void localAdminLoginCreatesAnAdminSessionThatCanUseTheAdminApi() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/v1/auth/local-login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"admin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@local.loquela.test"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles[1]").value("ADMIN"))
                .andReturn()
                .getRequest()
                .getSession(false);

        mockMvc.perform(get("/api/v1/admin/access-entries").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void localTestLoginRejectsUnknownAccountsWithTypedValidationProblem() throws Exception {
        mockMvc.perform(post("/api/v1/auth/local-login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"administrator"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("accountId"));
    }

    @Test
    void deletingAndDisabledAccountsReceiveExplicitSessionOutcomes() throws Exception {
        String deletingSubject = "deleting-browser-subject";
        UserId deletingId = userIdentityService.resolveOrCreateGoogle(deletingSubject, null, "Deleting User");
        AppUserEntity deleting = appUserRepository.findById(deletingId.value()).orElseThrow();
        deleting.setStatus(AppUserEntity.Status.DELETING);
        appUserRepository.saveAndFlush(deleting);

        mockMvc.perform(get("/api/v1/session")
                        .with(oidcLogin().idToken(token -> token.subject(deletingSubject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountState").value("DELETING"));

        String disabledSubject = "disabled-browser-subject";
        UserId disabledId = userIdentityService.resolveOrCreateGoogle(disabledSubject, null, "Disabled User");
        AppUserEntity disabled = appUserRepository.findById(disabledId.value()).orElseThrow();
        disabled.setStatus(AppUserEntity.Status.DISABLED);
        appUserRepository.saveAndFlush(disabled);

        mockMvc.perform(get("/api/v1/session")
                        .with(oidcLogin().idToken(token -> token.subject(disabledSubject))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void browserOpenApiContainsOnlyDtoBackedBrowserContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs/browser-v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/session']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/dashboard']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/sources'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/sources'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/sources/{sourceReference}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/source-uploads'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/local-login']").doesNotExist())
                .andExpect(jsonPath("$.paths['/webhooks/twilio/whatsapp']").doesNotExist())
                .andExpect(jsonPath("$.components.securitySchemes.browserSession").exists())
                .andExpect(jsonPath("$.components.schemas.ApiProblemDto").exists())
                .andExpect(jsonPath("$.components.schemas.DashboardDto").exists())
                .andExpect(jsonPath("$.components.schemas.PastedTextSourceDto").exists())
                .andExpect(jsonPath("$.components.schemas.SourceListDto").exists())
                .andExpect(jsonPath("$.components.schemas.AppUserEntity").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UserIdentityEntity").doesNotExist());
    }

    private SourceDocumentEntity source(
            UserId userId,
            String name,
            SourceDocumentEntity.Kind kind
    ) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(SourceId.random().value());
        source.setUserId(userId.value());
        source.setKind(kind);
        source.setDisplayName(name);
        source.setStatus(SourceDocumentEntity.Status.READY);
        source.setLanguage("es");
        return source;
    }
}
