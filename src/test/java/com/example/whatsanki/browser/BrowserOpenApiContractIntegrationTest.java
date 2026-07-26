package com.example.whatsanki.browser;

import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

class BrowserOpenApiContractIntegrationTest extends PostgreSqlIntegrationTest {

    private static final Path COMMITTED_CONTRACT = Path.of("openapi", "browser-v1.json");

    private final WebApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    BrowserOpenApiContractIntegrationTest(
            WebApplicationContext applicationContext,
            ObjectMapper objectMapper
    ) {
        this.applicationContext = applicationContext;
        this.objectMapper = objectMapper;
    }

    @Test
    void runtimeContractMatchesCommittedBrowserContract() throws Exception {
        MockMvc mockMvc = webAppContextSetup(applicationContext).build();
        String runtimeDocument = mockMvc.perform(get("/v3/api-docs/browser-v1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode runtimeContract = objectMapper.readTree(runtimeDocument);

        // Export is opt-in so normal test runs never mutate the worktree.
        if (Boolean.getBoolean("loquela.openapi.export")) {
            Files.createDirectories(COMMITTED_CONTRACT.getParent());
            Files.writeString(
                    COMMITTED_CONTRACT,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(runtimeContract)
                            + System.lineSeparator()
            );
        }

        assertThat(COMMITTED_CONTRACT)
                .as("Run ./mvnw -Dtest=BrowserOpenApiContractIntegrationTest "
                        + "-Dloquela.openapi.export=true test after an intentional contract change")
                .exists();
        JsonNode committedContract = objectMapper.readTree(Files.readString(COMMITTED_CONTRACT));
        assertThat(committedContract).isEqualTo(runtimeContract);
    }
}
