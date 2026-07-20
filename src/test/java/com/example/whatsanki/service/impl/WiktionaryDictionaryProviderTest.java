package com.example.whatsanki.service.impl;

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
class WiktionaryDictionaryProviderTest {

    @Mock private HttpClient httpClient;
    private WiktionaryDictionaryProvider provider;

    @BeforeEach
    void setUp() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.getDictionary().setUserAgent("WhatsAnki test (contact: test@example.com)");
        provider = new WiktionaryDictionaryProvider(configuration, httpClient, new ObjectMapper());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void mapsWikitextIntoAProvenancedProviderNeutralEntry() throws Exception {
        String body = """
                {"parse":{"title":"vivir","revid":12345,"wikitext":"==Spanish==\\n===Verb===\\n# to live\\n# [[reside|to reside]]\\n"}}
                """;
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(200, body));

        var result = provider.lookup(new DictionaryLookupRequestDto(" VIVIR ", new LanguagePairDto("es", "en")))
                .orElseThrow();

        assertThat(result.requestedTerm()).isEqualTo("VIVIR");
        assertThat(result.lemma()).isEqualTo("VIVIR");
        assertThat(result.searchTerms()).containsExactly("VIVIR");
        assertThat(result.senses()).extracting(sense -> sense.partOfSpeech())
                .containsExactly("verb", "verb");
        assertThat(result.senses()).extracting(sense -> sense.definition())
                .containsExactly("to live", "to reside");
        assertThat(result.provenance().providerId()).isEqualTo("wiktionary");
        assertThat(result.provenance().metadata()).containsEntry("revisionId", "12345")
                .containsEntry("edition", "en.wiktionary.org");

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().getHost()).isEqualTo("en.wiktionary.org");
        assertThat(request.getValue().uri().getQuery()).contains("action=parse").contains("page=VIVIR");
        assertThat(request.getValue().headers().firstValue("User-Agent"))
                .contains("WhatsAnki test (contact: test@example.com)");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void returnsEmptyWhenTheRequestedLanguageSectionIsAbsent() throws Exception {
        String body = """
                {"parse":{"title":"vivir","revid":1,"wikitext":"==English==\\n===Verb===\\n# to live\\n"}}
                """;
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(200, body));

        assertThat(provider.lookup(new DictionaryLookupRequestDto("vivir", new LanguagePairDto("es", "en"))))
                .isEmpty();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void mapsSpanishEditionLanguageAndPartOfSpeechTemplates() throws Exception {
        String body = """
                {"parse":{"title":"vivir","revid":99,"wikitext":"== {{lengua|es}} ==\\n=== Etimología 1 ===\\n==== {{verbo intransitivo|es}} ====\\n;1: Tener [[vida]].\\n;2: Tener su [[residencia]].\\n"}}
                """;
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        provider = new WiktionaryDictionaryProvider(configuration, httpClient, new ObjectMapper());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(200, body));

        var result = provider.lookup(new DictionaryLookupRequestDto("vivir", new LanguagePairDto("es", "es")))
                .orElseThrow();

        assertThat(result.senses()).extracting(sense -> sense.partOfSpeech())
                .containsExactly("verb", "verb");
        assertThat(result.senses()).extracting(sense -> sense.definition())
                .containsExactly("Tener vida.", "Tener su residencia.");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void mapsUpstreamRateLimitToTheStableRetryClassification() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(429, ""));

        assertThatThrownBy(() -> provider.lookup(new DictionaryLookupRequestDto("vivir", new LanguagePairDto("es", "en"))))
                .isInstanceOfSatisfying(DictionaryProviderException.class, exception ->
                        assertThat(exception.failureKind())
                                .isEqualTo(DictionaryProviderException.FailureKind.RATE_LIMITED)
                );
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void permanentlyRejectsAnOverLimitProviderPayloadWithoutReadingUnboundedData() throws Exception {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.getDictionary().setMaxResponseBytes(4);
        provider = new WiktionaryDictionaryProvider(configuration, httpClient, new ObjectMapper());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(200, "too large"));

        assertThatThrownBy(() -> provider.lookup(new DictionaryLookupRequestDto("vivir", new LanguagePairDto("es", "en"))))
                .isInstanceOfSatisfying(DictionaryProviderException.class, exception ->
                        assertThat(exception.failureKind())
                                .isEqualTo(DictionaryProviderException.FailureKind.PERMANENT)
                );
    }

    @Test
    void rejectsUnsupportedPairsWithoutSendingAnOutboundRequest() {
        LanguagePairDto pair = new LanguagePairDto("de", "en");

        assertThatThrownBy(() -> provider.lookup(new DictionaryLookupRequestDto("leben", pair)))
                .isInstanceOf(UnsupportedDictionaryLanguagePairException.class);
        verifyNoInteractions(httpClient);
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
            @Override public URI uri() { return URI.create("https://en.wiktionary.org/w/api.php"); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
