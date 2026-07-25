package com.example.whatsanki.service.dictionary;

import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionaryLookupRequestDto;
import com.example.whatsanki.dto.DictionaryProvenanceDto;
import com.example.whatsanki.dto.DictionarySenseDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.DictionaryProviderException;
import com.example.whatsanki.service.exception.UnsupportedDictionaryLanguagePairException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;

/**
 * Spanish monolingual adapter for the unofficial rae-api.com service.
 *
 * <p>The upstream schema is mapped into the same immutable dictionary contract used
 * by Wiktionary. This adapter deliberately advertises only {@code es:es}: RAE
 * definitions are Spanish and must never be mislabeled as English definitions.</p>
 */
@Service
@Slf4j
public class RaeDictionaryProvider implements DictionaryProvider {

    public static final String PROVIDER_ID = "rae";
    public static final String PROVIDER_VERSION = "rae-api-openapi-1.0.0";
    private static final String LICENSE = "RAE-CONTENT-RIGHTS-RESERVED";
    private static final LanguagePairDto SPANISH_MONOLINGUAL = new LanguagePairDto("es", "es");
    private static final List<LanguagePairDto> SUPPORTED_PAIRS = List.of(SPANISH_MONOLINGUAL);

    private final WhatsAnkiConfigurationDto configuration;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore permits;

    public RaeDictionaryProvider(
            WhatsAnkiConfigurationDto configuration,
            @Qualifier("raeHttpClient") HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.configuration = configuration;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.permits = new Semaphore(configuration.getDictionary().getMaxConcurrentRequests());
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public String providerVersion() {
        return PROVIDER_VERSION;
    }

    @Override
    public List<LanguagePairDto> supportedPairs() {
        return SUPPORTED_PAIRS;
    }

    @Override
    public Optional<DictionaryEntryDto> lookup(DictionaryLookupRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (!supports(request.languagePair())) {
            throw new UnsupportedDictionaryLanguagePairException(PROVIDER_ID, request.languagePair());
        }
        if (!permits.tryAcquire()) {
            log.warn(
                    "Dictionary provider concurrency limit reached providerId={} sourceLanguage={} definitionLanguage={}",
                    PROVIDER_ID,
                    request.languagePair().sourceLanguage(),
                    request.languagePair().definitionLanguage()
            );
            throw DictionaryProviderException.rateLimited(PROVIDER_ID, null);
        }

        try {
            URI endpoint = endpointFor(request.requestedTerm());
            HttpResponse<InputStream> response = send(endpoint);
            try (InputStream body = response.body()) {
                int status = response.statusCode();
                if (status == 404) {
                    log.info(
                            "RAE dictionary lookup completed providerId={} sourceLanguage=es definitionLanguage=es termCharacters={} found=false",
                            PROVIDER_ID,
                            characterCount(request.requestedTerm())
                    );
                    return Optional.empty();
                }
                if (status == 429) {
                    throw DictionaryProviderException.rateLimited(PROVIDER_ID, null);
                }
                if (status >= 500) {
                    throw DictionaryProviderException.temporary(PROVIDER_ID, null);
                }
                if (status < 200 || status >= 300) {
                    throw DictionaryProviderException.permanent(PROVIDER_ID, null);
                }

                String json = readBounded(
                        body,
                        response,
                        configuration.getDictionary().getMaxResponseBytes()
                );
                Optional<DictionaryEntryDto> result;
                try {
                    result = mapResponse(json, request, endpoint);
                } catch (IOException | IllegalArgumentException exception) {
                    // A changed or malformed schema is deterministic for this adapter.
                    // Never include the upstream body in an exception or log.
                    throw DictionaryProviderException.permanent(PROVIDER_ID, exception);
                }
                log.info(
                        "RAE dictionary lookup completed providerId={} sourceLanguage=es definitionLanguage=es termCharacters={} found={} senseCount={}",
                        PROVIDER_ID,
                        characterCount(request.requestedTerm()),
                        result.isPresent(),
                        result.map(entry -> entry.senses().size()).orElse(0)
                );
                return result;
            }
        } catch (DictionaryProviderException exception) {
            throw exception;
        } catch (ResponseTooLargeSignal exception) {
            throw DictionaryProviderException.permanent(PROVIDER_ID, exception);
        } catch (HttpTimeoutException exception) {
            throw DictionaryProviderException.timeout(PROVIDER_ID, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw DictionaryProviderException.temporary(PROVIDER_ID, exception);
        } catch (IOException exception) {
            throw DictionaryProviderException.temporary(PROVIDER_ID, exception);
        } catch (RuntimeException exception) {
            throw DictionaryProviderException.permanent(PROVIDER_ID, exception);
        } finally {
            permits.release();
        }
    }

    private HttpResponse<InputStream> send(URI endpoint) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(configuration.getDictionary().getReadTimeout())
                .header("Accept", "application/json")
                .header("User-Agent", configuration.getDictionary().getUserAgent())
                .GET();
        String apiKey = configuration.getDictionary().getRaeApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            // A header keeps credentials out of request URLs, access logs, and
            // persisted provenance.
            request.header("X-API-Key", apiKey.strip());
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    private URI endpointFor(String term) {
        URI base = configuration.getDictionary().getRaeBaseUrl();
        List<String> allowedHosts = configuration.getDictionary().getRaeAllowedHosts();
        if (base == null
                || !"https".equalsIgnoreCase(base.getScheme())
                || base.getHost() == null
                || !allowedHosts.contains(base.getHost())
                || base.getUserInfo() != null
                || base.getQuery() != null
                || base.getFragment() != null) {
            throw DictionaryProviderException.permanent(PROVIDER_ID, null);
        }
        String baseValue = base.toString().replaceAll("/+$", "");
        String encodedTerm = URLEncoder.encode(term.strip(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        try {
            return URI.create(baseValue + "/api/words/" + encodedTerm);
        } catch (IllegalArgumentException exception) {
            throw DictionaryProviderException.permanent(PROVIDER_ID, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<DictionaryEntryDto> mapResponse(
            String json,
            DictionaryLookupRequestDto request,
            URI endpoint
    ) throws IOException {
        Map<String, Object> root = objectMapper.readValue(json, Map.class);
        if (!Boolean.TRUE.equals(root.get("ok"))) {
            throw new IllegalArgumentException("RAE response did not report success");
        }
        Map<?, ?> data = requiredMap(root.get("data"), "data");
        String lemma = requiredString(data.get("word"), "data.word");
        Object meaningsValue = data.get("meanings");
        if (!(meaningsValue instanceof List<?> meanings)) {
            throw new IllegalArgumentException("RAE response does not contain data.meanings");
        }

        List<DictionarySenseDto> senses = new ArrayList<>();
        int meaningPosition = 0;
        for (Object meaningValue : meanings) {
            meaningPosition++;
            Map<?, ?> meaning = requiredMap(meaningValue, "meaning");
            int homonymIndex = integerValue(meaning.get("homonym_index"), meaningPosition);
            Object sensesValue = meaning.get("senses");
            if (!(sensesValue instanceof List<?> upstreamSenses)) {
                throw new IllegalArgumentException("RAE meaning does not contain senses");
            }
            int sensePosition = 0;
            for (Object senseValue : upstreamSenses) {
                sensePosition++;
                Map<?, ?> sense = requiredMap(senseValue, "sense");
                String description = optionalString(sense.get("description"));
                if (description.isBlank()) {
                    continue;
                }
                String partOfSpeech = optionalString(sense.get("category"));
                if (partOfSpeech.isBlank()) {
                    partOfSpeech = "unknown";
                }
                int meaningNumber = integerValue(sense.get("meaning_number"), sensePosition);
                senses.add(new DictionarySenseDto(
                        partOfSpeech,
                        normalizeText(description),
                        PROVIDER_ID + ":" + encodeReference(lemma)
                                + "#homonym-" + homonymIndex + "-sense-" + meaningNumber
                ));
            }
        }
        if (senses.isEmpty()) {
            return Optional.empty();
        }

        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("apiSchemaVersion", "1.0.0");
        metadata.put("entryWord", lemma);
        metadata.put("meaningCount", Integer.toString(meanings.size()));
        return Optional.of(new DictionaryEntryDto(
                request.requestedTerm(),
                lemma,
                List.of(lemma),
                senses,
                request.languagePair(),
                new DictionaryProvenanceDto(
                        PROVIDER_ID,
                        PROVIDER_VERSION,
                        PROVIDER_ID + ":" + encodeReference(lemma),
                        endpoint.toString(),
                        LICENSE,
                        "Diccionario de la lengua española content delivered by the unofficial RAE API.",
                        metadata
                )
        ));
    }

    private String readBounded(
            InputStream input,
            HttpResponse<?> response,
            long maximumBytes
    ) throws IOException {
        long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (declared > maximumBytes) {
            throw new ResponseTooLargeSignal();
        }
        byte[] buffer = new byte[8192];
        int count;
        long total = 0;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > maximumBytes) {
                throw new ResponseTooLargeSignal();
            }
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private Map<?, ?> requiredMap(Object value, String field) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException("RAE response does not contain " + field);
    }

    private String requiredString(Object value, String field) {
        String text = optionalString(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException("RAE response does not contain " + field);
        }
        return normalizeText(text);
    }

    private String optionalString(Object value) {
        return value instanceof String text ? text : "";
    }

    private int integerValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private String normalizeText(String value) {
        return value.replace("\u0000", "").replaceAll("\\s+", " ").strip();
    }

    private String encodeReference(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private int characterCount(String value) {
        return value.codePointCount(0, value.length());
    }

    private static final class ResponseTooLargeSignal extends RuntimeException {
    }
}
