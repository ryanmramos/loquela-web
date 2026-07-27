package com.example.whatsanki.browser;

import com.example.whatsanki.controller.RequestCorrelationFilter;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(properties = {
        "whatsanki.security.google-oauth-enabled=true",
        "spring.security.oauth2.client.registration.google.client-id=isolated-test-client",
        "spring.security.oauth2.client.registration.google.client-secret=isolated-test-secret",
        "spring.security.oauth2.client.registration.google.scope=openid,profile,email",
        "spring.security.oauth2.client.registration.google.redirect-uri="
                + "http://localhost:4200/login/oauth2/code/google"
})
class BrowserGoogleOAuthConfigurationIntegrationTest extends PostgreSqlIntegrationTest {

    private final WebApplicationContext applicationContext;
    private final RequestCorrelationFilter requestCorrelationFilter;
    private MockMvc mockMvc;

    BrowserGoogleOAuthConfigurationIntegrationTest(
            WebApplicationContext applicationContext,
            RequestCorrelationFilter requestCorrelationFilter
    ) {
        this.applicationContext = applicationContext;
        this.requestCorrelationFilter = requestCorrelationFilter;
    }

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = webAppContextSetup(applicationContext)
                .addFilters(requestCorrelationFilter)
                .apply(springSecurity())
                .build();
    }

    @Test
    void isolatedGoogleLoginUsesTheFrontendCallbackWithoutContactingGoogle() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/api/v1/auth/google")
                        .param("returnTo", "/app/cards")
                        .session(session))
                .andExpect(status().isFound());

        String providerLocation = mockMvc.perform(get("/oauth2/authorization/google").session(session))
                .andExpect(status().isFound())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        assertThat(providerLocation).isNotNull();
        String decodedLocation = URLDecoder.decode(providerLocation, StandardCharsets.UTF_8);
        assertThat(decodedLocation)
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth")
                .contains("client_id=isolated-test-client")
                .contains("redirect_uri=http://localhost:4200/login/oauth2/code/google")
                .contains("scope=openid profile email");
    }
}
