package com.example.whatsanki.service.twilio;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.util.TwilioMediaUrlPolicy;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TwilioMediaDownloaderHttpIntegrationTest {

    private HttpServer apiServer;
    private HttpServer cdnServer;

    @BeforeEach
    void setUp() throws Exception {
        apiServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        cdnServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    @AfterEach
    void tearDown() {
        apiServer.stop(0);
        cdnServer.stop(0);
    }

    @Test
    void followsRealHttpRedirectWithoutLeakingAuthorization() throws Exception {
        byte[] content = "%PDF-real-http-test".getBytes(StandardCharsets.US_ASCII);
        AtomicReference<String> apiAuthorization = new AtomicReference<>();
        AtomicReference<String> cdnAuthorization = new AtomicReference<>();

        cdnServer.createContext("/signed-media", exchange -> {
            cdnAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
        });
        cdnServer.start();
        URI redirectUri = URI.create("http://127.0.0.1:" + cdnServer.getAddress().getPort() + "/signed-media");

        apiServer.createContext("/media", exchange -> {
            apiAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().add("Location", redirectUri.toString());
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        apiServer.start();
        URI mediaUri = URI.create("http://127.0.0.1:" + apiServer.getAddress().getPort() + "/media");

        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.getTwilio().setAccountSid("AC123");
        configuration.getTwilio().setAuthToken("token");
        HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        TwilioMediaDownloaderImpl downloader = new TwilioMediaDownloaderImpl(
                configuration, mock(TwilioMediaUrlPolicy.class), httpClient
        );

        try (var downloaded = downloader.download(mediaUri)) {
            assertThat(java.nio.file.Files.readAllBytes(downloaded.path())).isEqualTo(content);
        }
        assertThat(apiAuthorization.get()).startsWith("Basic ");
        assertThat(cdnAuthorization.get()).isNull();
    }
}
