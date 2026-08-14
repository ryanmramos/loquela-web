package com.example.whatsanki.browser;

import com.example.whatsanki.controller.RequestCorrelationFilter;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@TestPropertySource(properties = "whatsanki.demo.enabled=true")
class DemoSecurityIntegrationTest extends PostgreSqlIntegrationTest {
    private static final List<String> DOMAIN_TABLES = List.of(
            "app_user", "user_identity", "source_document", "passage", "lookup_session",
            "deck", "card", "export_request", "source_upload", "background_job",
            "inbound_message", "outbound_message", "practice_session", "practice_session_item",
            "practice_response"
    );

    private final WebApplicationContext applicationContext;
    private final RequestCorrelationFilter correlationFilter;
    private final JdbcTemplate jdbcTemplate;
    private MockMvc mockMvc;

    DemoSecurityIntegrationTest(WebApplicationContext applicationContext,
                                RequestCorrelationFilter correlationFilter,
                                JdbcTemplate jdbcTemplate) {
        this.applicationContext = applicationContext;
        this.correlationFilter = correlationFilter;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(applicationContext)
                .addFilters(correlationFilter)
                .apply(springSecurity())
                .build();
    }

    @Test
    void anonymousDemoRequiresCsrfCannotReachProductionApisAndDoesNotPersistDomainRows() throws Exception {
        Map<String, Long> before = counts();
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/api/v1/demo/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(post("/api/v1/demo/workspace").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/demo/workspace").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/sources").session(session))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/demo/sources/pasted-text")
                        .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Quiero vivir bien. Vivimos cerca del mar.\"}"))
                .andExpect(status().isAccepted());
        for (int attempt = 0; attempt < 100; attempt++) {
            String body = mockMvc.perform(get("/api/v1/demo/workspace").session(session))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            if (body.contains("\"READY\"")) break;
            Thread.sleep(10);
        }
        String lookup = mockMvc.perform(post("/api/v1/demo/lookups")
                        .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"term\":\"vivir\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitions").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String lookupReference = new tools.jackson.databind.ObjectMapper()
                .readTree(lookup).path("reference").asText();
        mockMvc.perform(post("/api/v1/demo/cards")
                        .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lookupReference\":\"" + lookupReference + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/v1/demo/cards")
                        .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lookupReference\":\"" + lookupReference
                                + "\",\"kind\":\"DEFINITION\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/demo/exports/download").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"loquela-demo.apkg\""))
                .andExpect(header().string("Content-Type", "application/vnd.anki"));
        mockMvc.perform(post("/api/v1/demo/workspace/reset").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards").isEmpty())
                .andExpect(jsonPath("$.sources").isEmpty());
        mockMvc.perform(post("/api/v1/demo/workspace/exit").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void mutationWithoutCsrfFailsClosed() throws Exception {
        mockMvc.perform(post("/api/v1/demo/lookups")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"term\":\"vivir\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedSessionsCannotOperateAnonymousDemoWorkspaces() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/api/v1/demo/capabilities").session(session).with(user("member")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/demo/workspace")
                        .session(session).with(user("member")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    private Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : DOMAIN_TABLES) {
            Long exists = jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.tables where table_schema='public' and table_name=?",
                    Long.class, table);
            if (exists != null && exists == 1) {
                counts.put(table, jdbcTemplate.queryForObject("select count(*) from " + table, Long.class));
            }
        }
        return counts;
    }
}
