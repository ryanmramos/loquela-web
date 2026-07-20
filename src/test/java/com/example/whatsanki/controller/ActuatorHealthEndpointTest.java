package com.example.whatsanki.controller;

import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorHealthEndpointTest extends PostgreSqlIntegrationTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void exposesDetailFreeLivenessProbe() throws Exception {
        assertDetailFreeUpResponse("/actuator/health/liveness");
    }

    @Test
    void exposesDetailFreeDatabaseBackedReadinessProbe() throws Exception {
        assertDetailFreeUpResponse("/actuator/health/readiness");
    }

    private void assertDetailFreeUpResponse(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
        assertThat(response.body()).doesNotContain("components");
    }
}
