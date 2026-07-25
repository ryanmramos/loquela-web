package com.example.whatsanki.service.dictionary;

import com.example.whatsanki.dto.DictionaryLookupRequestDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.DictionaryProviderException;
import com.example.whatsanki.service.exception.UnsupportedDictionaryLanguagePairException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaeDictionaryProviderTest {

    @Mock private HttpClient httpClient;
    private WhatsAnkiConfigurationDto configuration;
    private RaeDictionaryProvider provider;

    @BeforeEach
    void setUp() {
        configuration = new WhatsAnkiConfigurationDto();
        configuration.getDictionary().setRaeApiKey("test-secret-key");
        configuration.getDictionary().setUserAgent("WhatsAnki test (contact: test@example.com)");
        provider = new RaeDictionaryProvider(configuration, httpClient, new ObjectMapper());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void mapsSpanishSensesAndSendsTheApiKeyOnlyInAHeader() throws Exception {
        String body = """
                {
                  "ok": true,
                  "data": {
                    "word": "vivir",
                    "meanings": [{
                      "homonym_index": 0,
                      "senses": [
                        {"meaning_number": 1, "category": "verb", "description": "Tener vida."},
                        {"meaning_number": 2, "category": "verb", "description": "Habitar o morar."}
                      ],
                      "locutions": [],
                      "conjugations": {}
                    }]
                  }
                }
                """;
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(200, body));

        var result = provider.lookup(request("vivir", "es", "es")).orElseThrow();

        assertThat(result.lemma()).isEqualTo("vivir");
        assertThat(result.senses()).extracting(sense -> sense.partOfSpeech())
                .containsExactly("verb", "verb");
        assertThat(result.senses()).extracting(sense -> sense.definition())
                .containsExactly("Tener vida.", "Habitar o morar.");
        assertThat(result.senses()).extracting(sense -> sense.providerReference())
                .containsExactly("rae:vivir#homonym-0-sense-1", "rae:vivir#homonym-0-sense-2");
        assertThat(result.provenance().providerId()).isEqualTo("rae");
        assertThat(result.provenance().licenseIdentifier()).isEqualTo("RAE-CONTENT-RIGHTS-RESERVED");
        assertThat(result.provenance().metadata())
                .containsEntry("apiSchemaVersion", "1.0.0")
                .containsEntry("meaningCount", "1");

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().toString())
                .isEqualTo("https://rae-api.com/api/words/vivir")
                .doesNotContain("test-secret-key");
        assertThat(request.getValue().headers().firstValue("X-API-Key"))
                .contains("test-secret-key");
        assertThat(request.getValue().headers().firstValue("User-Agent"))
                .contains("WhatsAnki test (contact: test@example.com)");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pathEncodesUnicodeAndOmitsTheHeaderWhenNoKeyIsConfigured() throws Exception {
        configuration.getDictionary().setRaeApiKey(" ");
        provider = new RaeDictionaryProvider(configuration, httpClient, new ObjectMapper());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(404, """
                        {"ok":false,"error":"NOT_FOUND","suggestions":[]}
                        """));

        assertThat(provider.lookup(request("acción útil", "es", "es"))).isEmpty();

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().getRawPath())
                .isEqualTo("/api/words/acci%C3%B3n%20%C3%BAtil");
        assertThat(request.getValue().headers().firstValue("X-API-Key")).isEmpty();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void returnsEmptyOnNotFoundSoTheConfiguredRouterCanUseWiktionary() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(404, """
                        {"ok":false,"error":"NOT_FOUND","suggestions":["vivaz"]}
                        """));

        assertThat(provider.lookup(request("inexistente", "es", "es"))).isEmpty();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void mapsRateLimitingToTheStableRetryClassification() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(429, """
                        {"ok":false,"error":"RATE_LIMIT_EXCEEDED","retry_after":30}
                        """));

        assertThatThrownBy(() -> provider.lookup(request("vivir", "es", "es")))
                .isInstanceOfSatisfying(DictionaryProviderException.class, exception ->
                        assertThat(exception.failureKind())
                                .isEqualTo(DictionaryProviderException.FailureKind.RATE_LIMITED)
                );
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void mapsServerErrorsToTemporaryFailureWithoutLeakingTheBody() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(503, "sensitive upstream diagnostic"));

        assertThatThrownBy(() -> provider.lookup(request("vivir", "es", "es")))
                .isInstanceOfSatisfying(DictionaryProviderException.class, exception -> {
                    assertThat(exception.failureKind())
                            .isEqualTo(DictionaryProviderException.FailureKind.TEMPORARY);
                    assertThat(exception).hasMessageNotContaining("sensitive upstream diagnostic");
                });
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void treatsAMalformedSuccessPayloadAsAPermanentAdapterFailure() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(200, """
                        {"ok":true,"data":{"word":"vivir"}}
                        """));

        assertThatThrownBy(() -> provider.lookup(request("vivir", "es", "es")))
                .isInstanceOfSatisfying(DictionaryProviderException.class, exception ->
                        assertThat(exception.failureKind())
                                .isEqualTo(DictionaryProviderException.FailureKind.PERMANENT)
                );
    }

    @Test
    void rejectsNonSpanishDefinitionPairsWithoutAnOutboundRequest() {
        assertThatThrownBy(() -> provider.lookup(request("vivir", "es", "en")))
                .isInstanceOf(UnsupportedDictionaryLanguagePairException.class);
        verifyNoInteractions(httpClient);
    }

    private DictionaryLookupRequestDto request(String term, String source, String definition) {
        return new DictionaryLookupRequestDto(term, new LanguagePairDto(source, definition));
    }

    private HttpResponse<java.io.InputStream> response(int status, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return new HttpResponse<>() {
            @Override public int statusCode() { return status; }
            @Override public HttpRequest request() { return null; }
            @Override public Optional<HttpResponse<java.io.InputStream>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() { return HttpHeaders.of(
                    Map.of("Content-Length", List.of(String.valueOf(bytes.length))), (name, value) -> true
            ); }
            @Override public java.io.InputStream body() { return new ByteArrayInputStream(bytes); }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return URI.create("https://rae-api.com/api/words/vivir"); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
