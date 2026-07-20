package com.example.whatsanki.service.dictionary;

import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionaryLookupRequestDto;
import com.example.whatsanki.dto.DictionaryProvenanceDto;
import com.example.whatsanki.dto.DictionarySenseDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.dictionary.DictionaryProvider;
import com.example.whatsanki.service.exception.DictionaryProviderException;
import com.example.whatsanki.service.exception.UnsupportedDictionaryLanguagePairException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounded Wikimedia Action API prototype. It maps only a small, documented subset of
 * wikitext into the provider-neutral contract; it never returns raw provider markup.
 * A curated import or commercial adapter can replace this implementation without
 * changing lookup, persistence, card, or export code.
 */
@Service
@Slf4j
public class WiktionaryDictionaryProvider implements DictionaryProvider {

    public static final String PROVIDER_ID = "wiktionary";
    public static final String PROVIDER_VERSION = "action-api-wikitext-v1";
    private static final String LICENSE = "CC-BY-SA-4.0 AND GFDL-1.3-or-later";
    private static final List<LanguagePairDto> SUPPORTED_PAIRS = List.of(
            new LanguagePairDto("es", "en"),
            new LanguagePairDto("en", "es"),
            new LanguagePairDto("es", "es"),
            new LanguagePairDto("en", "en"),
            new LanguagePairDto("fr", "en")
    );
    private static final Map<String, String> ENGLISH_SECTION_NAMES = Map.of(
            "en", "English", "es", "Spanish", "fr", "French"
    );
    private static final Map<String, String> SPANISH_SECTION_NAMES = Map.of(
            "en", "inglés", "es", "español", "fr", "francés"
    );
    // A level-two language heading must not consume a level-three part-of-speech
    // heading; otherwise the selected section would end before its definitions.
    private static final Pattern LANGUAGE_HEADING = Pattern.compile("(?m)^==(?![=])\\s*(.+?)\\s*==(?!=)\\s*$");
    private static final Pattern POS_HEADING = Pattern.compile("(?m)^===+\\s*(.+?)\\s*===+\\s*$");
    private static final Pattern DEFINITION = Pattern.compile("(?m)^#(?![#:*])\\s*(.+?)\\s*$");
    // Several non-English editions use numbered semicolon definitions (for example
    // Spanish Wiktionary's ";1:") instead of MediaWiki's conventional "#" lines.
    private static final Pattern NUMBERED_DEFINITION = Pattern.compile("(?m)^;\\s*\\d+\\s*:\\s*(.+?)\\s*$");
    private static final Pattern INFLECTION_OF = Pattern.compile(
            "\\{\\{(?:inflection of|infl of|forma de)\\|[^|}]+\\|([^|}]+)", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LINK = Pattern.compile("\\[\\[([^\\]|]+)(?:\\|([^\\]]+))?]]");
    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{[^{}]*}}");
    private static final Pattern HTML = Pattern.compile("<[^>]+>");
    private static final Pattern REF = Pattern.compile("<ref[^>]*>.*?</ref>|<ref[^>]*/>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final WhatsAnkiConfigurationDto configuration;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore permits;

    public WiktionaryDictionaryProvider(
            WhatsAnkiConfigurationDto configuration,
            @Qualifier("wiktionaryHttpClient") HttpClient httpClient,
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
            log.warn("Dictionary provider concurrency limit reached providerId={} sourceLanguage={} definitionLanguage={}",
                    PROVIDER_ID, request.languagePair().sourceLanguage(), request.languagePair().definitionLanguage());
            throw DictionaryProviderException.rateLimited(PROVIDER_ID, null);
        }
        try {
            URI endpoint = endpointFor(request.languagePair());
            HttpResponse<InputStream> response = send(endpoint, request.requestedTerm());
            try (InputStream body = response.body()) {
                int status = response.statusCode();
                if (status == 429) {
                    throw DictionaryProviderException.rateLimited(PROVIDER_ID, null);
                }
                if (status >= 500) {
                    throw DictionaryProviderException.temporary(PROVIDER_ID, null);
                }
                if (status < 200 || status >= 300) {
                    throw DictionaryProviderException.permanent(PROVIDER_ID, null);
                }
                String json = readBounded(body, response, configuration.getDictionary().getMaxResponseBytes());
                Optional<DictionaryEntryDto> result;
                try {
                    result = mapResponse(json, request, endpoint);
                } catch (IOException | IllegalArgumentException exception) {
                    // A structurally invalid response will not improve on job retry and
                    // must not expose raw upstream content in diagnostics.
                    throw DictionaryProviderException.permanent(PROVIDER_ID, exception);
                }
                log.info("Wiktionary lookup completed providerId={} sourceLanguage={} definitionLanguage={} termCharacters={} found={}",
                        PROVIDER_ID,
                        request.languagePair().sourceLanguage(),
                        request.languagePair().definitionLanguage(),
                        request.requestedTerm().codePointCount(0, request.requestedTerm().length()),
                        result.isPresent());
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
            // JSON/wikitext changes are a deterministic adapter rejection until this
            // pinned mapper is updated; do not leak upstream bodies into job logs.
            throw DictionaryProviderException.permanent(PROVIDER_ID, exception);
        } finally {
            permits.release();
        }
    }

    private HttpResponse<InputStream> send(URI endpoint, String term) throws IOException, InterruptedException {
        String separator = endpoint.getQuery() == null ? "?" : "&";
        URI uri = URI.create(endpoint + separator
                + "action=parse&prop=wikitext%7Crevid&format=json&formatversion=2&page="
                + URLEncoder.encode(term, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(configuration.getDictionary().getReadTimeout())
                .header("Accept", "application/json")
                .header("User-Agent", configuration.getDictionary().getUserAgent())
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    private URI endpointFor(LanguagePairDto pair) {
        String template = configuration.getDictionary().getWiktionaryBaseUrlTemplate();
        URI endpoint;
        try {
            endpoint = URI.create(template.replace("{definitionLanguage}", pair.definitionLanguage()));
        } catch (IllegalArgumentException exception) {
            throw DictionaryProviderException.permanent(PROVIDER_ID, exception);
        }
        if (!"https".equalsIgnoreCase(endpoint.getScheme())
                || endpoint.getHost() == null
                || !configuration.getDictionary().getWiktionaryAllowedHosts().contains(endpoint.getHost())) {
            throw DictionaryProviderException.permanent(PROVIDER_ID, null);
        }
        return endpoint;
    }

    private String readBounded(InputStream input, HttpResponse<?> response, long maximumBytes) throws IOException {
        long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (declared > maximumBytes) {
            throw new ResponseTooLargeSignal();
        }
        byte[] buffer = new byte[8192];
        int count;
        long total = 0;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > maximumBytes) {
                throw new ResponseTooLargeSignal();
            }
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private Optional<DictionaryEntryDto> mapResponse(String json, DictionaryLookupRequestDto request, URI endpoint)
            throws IOException {
        Map<String, Object> root = objectMapper.readValue(json, Map.class);
        if (root.containsKey("error")) {
            return Optional.empty();
        }
        Object parseValue = root.get("parse");
        if (!(parseValue instanceof Map<?, ?> parse)) {
            throw new IllegalArgumentException("Wiktionary response does not contain parse object");
        }
        Object wikitextValue = parse.get("wikitext");
        if (!(wikitextValue instanceof String wikitext) || wikitext.isBlank()) {
            return Optional.empty();
        }
        String section = sourceSection(wikitext, request.languagePair());
        if (section == null) {
            return Optional.empty();
        }
        String lemma = inflectionLemma(section).orElse(request.requestedTerm());
        List<DictionarySenseDto> senses = senses(section, request.languagePair().definitionLanguage());
        if (senses.isEmpty()) {
            return Optional.empty();
        }
        String pageTitle = parse.get("title") instanceof String title && !title.isBlank()
                ? title.strip() : request.requestedTerm();
        String revision = parse.get("revid") instanceof Number number ? number.toString() : "unknown";
        String edition = request.languagePair().definitionLanguage();
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("edition", edition + ".wiktionary.org");
        metadata.put("pageTitle", pageTitle);
        metadata.put("revisionId", revision);
        return Optional.of(new DictionaryEntryDto(
                request.requestedTerm(),
                cleanText(lemma),
                List.of(cleanText(lemma)),
                senses,
                request.languagePair(),
                new DictionaryProvenanceDto(
                        PROVIDER_ID,
                        PROVIDER_VERSION,
                        "wiktionary:" + edition + ":" + pageTitle + ":" + revision,
                        endpoint.getScheme() + "://" + endpoint.getHost() + "/wiki/"
                                + URLEncoder.encode(pageTitle, StandardCharsets.UTF_8),
                        LICENSE,
                        "Definitions from " + edition + ".wiktionary.org; see source page and revision.",
                        metadata
                )
        ));
    }

    private String sourceSection(String wikitext, LanguagePairDto pair) {
        Map<String, String> names = "es".equals(pair.definitionLanguage())
                ? SPANISH_SECTION_NAMES : ENGLISH_SECTION_NAMES;
        String expected = names.get(pair.sourceLanguage());
        if (expected == null) {
            return null;
        }
        Matcher headings = LANGUAGE_HEADING.matcher(wikitext);
        while (headings.find()) {
            if (!languageHeadingMatches(headings.group(1), expected, pair.sourceLanguage())) {
                continue;
            }
            int start = headings.end();
            int end = wikitext.length();
            if (headings.find()) {
                end = headings.start();
            }
            return wikitext.substring(start, end);
        }
        return null;
    }

    private List<DictionarySenseDto> senses(String section, String definitionLanguage) {
        List<DictionarySenseDto> mapped = new ArrayList<>();
        Matcher headings = POS_HEADING.matcher(section);
        int cursor = 0;
        String partOfSpeech = "definition";
        while (headings.find()) {
            mapped.addAll(definitions(section.substring(cursor, headings.start()), partOfSpeech));
            partOfSpeech = normalizePartOfSpeech(headings.group(1));
            cursor = headings.end();
        }
        mapped.addAll(definitions(section.substring(cursor), partOfSpeech));
        return List.copyOf(mapped);
    }

    private List<DictionarySenseDto> definitions(String text, String partOfSpeech) {
        List<DictionarySenseDto> result = new ArrayList<>();
        addDefinitions(result, DEFINITION.matcher(text), partOfSpeech);
        addDefinitions(result, NUMBERED_DEFINITION.matcher(text), partOfSpeech);
        return result;
    }

    private void addDefinitions(
            List<DictionarySenseDto> target,
            Matcher definitions,
            String partOfSpeech
    ) {
        while (definitions.find()) {
            String definition = cleanText(definitions.group(1));
            if (!definition.isBlank()) {
                target.add(new DictionarySenseDto(partOfSpeech, definition,
                        PROVIDER_ID + ":" + Integer.toUnsignedString(definition.hashCode())));
            }
        }
    }

    private boolean languageHeadingMatches(String rawHeading, String expectedName, String sourceLanguage) {
        String cleaned = cleanHeading(rawHeading);
        if (expectedName.equalsIgnoreCase(cleaned)) {
            return true;
        }
        // Wiktionary editions increasingly use a language-code template, which is
        // more stable than translated display names such as "español" or "Spanish".
        return cleaned.isBlank() && rawHeading.strip().matches(
                "(?i)\\{\\{\\s*lengua\\s*\\|\\s*" + Pattern.quote(sourceLanguage) + "(?:\\s*\\|[^}]*)?}}"
        );
    }

    private String normalizePartOfSpeech(String rawHeading) {
        String cleaned = cleanHeading(rawHeading).toLowerCase(Locale.ROOT);
        if (cleaned.isBlank()) {
            Matcher template = Pattern.compile("(?i)\\{\\{\\s*([^|}]+)(?:\\|[^}]*)?}}").matcher(rawHeading.strip());
            if (template.matches()) {
                cleaned = template.group(1).strip().toLowerCase(Locale.ROOT);
            }
        }
        if (cleaned.contains("verb") || cleaned.contains("verbo")) return "verb";
        if (cleaned.contains("noun") || cleaned.contains("sustantivo")) return "noun";
        if (cleaned.contains("adjective") || cleaned.contains("adjetivo")) return "adjective";
        if (cleaned.contains("adverb") || cleaned.contains("adverbio")) return "adverb";
        if (cleaned.contains("pronoun") || cleaned.contains("pronombre")) return "pronoun";
        if (cleaned.contains("preposition") || cleaned.contains("preposición")) return "preposition";
        return cleaned.isBlank() ? "definition" : cleaned;
    }

    private Optional<String> inflectionLemma(String section) {
        Matcher matcher = INFLECTION_OF.matcher(section);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private String cleanHeading(String value) {
        return cleanText(value).replaceAll("(?i)^language:", "").strip();
    }

    private String cleanText(String value) {
        String withoutRefs = REF.matcher(value).replaceAll("");
        Matcher links = LINK.matcher(withoutRefs);
        StringBuffer linked = new StringBuffer();
        while (links.find()) {
            links.appendReplacement(linked, Matcher.quoteReplacement(
                    links.group(2) == null ? links.group(1) : links.group(2)
            ));
        }
        links.appendTail(linked);
        String cleaned = TEMPLATE.matcher(linked.toString()).replaceAll("");
        cleaned = HTML.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replace("'''", "").replace("''", "");
        cleaned = cleaned.replaceAll("(?m)^[:*;]+", "");
        return WHITESPACE.matcher(Normalizer.normalize(cleaned, Normalizer.Form.NFKC)).replaceAll(" ").strip();
    }

    private static final class ResponseTooLargeSignal extends IOException {
        private ResponseTooLargeSignal() {
            super("dictionary response exceeds configured size");
        }
    }
}
