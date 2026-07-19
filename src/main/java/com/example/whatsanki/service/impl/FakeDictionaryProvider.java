package com.example.whatsanki.service.impl;

import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionaryLookupRequestDto;
import com.example.whatsanki.dto.DictionaryProvenanceDto;
import com.example.whatsanki.dto.DictionarySenseDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.service.DictionaryProvider;
import com.example.whatsanki.service.exception.UnsupportedDictionaryLanguagePairException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class FakeDictionaryProvider implements DictionaryProvider {

    public static final String PROVIDER_ID = "fake";
    public static final String PROVIDER_VERSION = "fixture-v1";

    private final Map<LookupKey, Fixture> fixturesByTerm;
    private final List<LanguagePairDto> supportedPairs;

    public FakeDictionaryProvider() {
        List<Fixture> fixtures = fixtures();
        fixturesByTerm = indexFixtures(fixtures);
        LinkedHashSet<LanguagePairDto> fixturePairs = new LinkedHashSet<>();
        fixtures.forEach(fixture -> fixturePairs.add(fixture.languagePair()));
        supportedPairs = List.copyOf(fixturePairs);
        log.info(
                "Initialized fake dictionary provider fixtureCount={} supportedPairCount={}",
                fixtures.size(),
                supportedPairs.size()
        );
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
        return supportedPairs;
    }

    @Override
    public Optional<DictionaryEntryDto> lookup(DictionaryLookupRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (!supports(request.languagePair())) {
            log.warn(
                    "Rejected unsupported dictionary pair providerId={} sourceLanguage={} definitionLanguage={}",
                    PROVIDER_ID,
                    request.languagePair().sourceLanguage(),
                    request.languagePair().definitionLanguage()
            );
            throw new UnsupportedDictionaryLanguagePairException(PROVIDER_ID, request.languagePair());
        }

        Fixture fixture = fixturesByTerm.get(new LookupKey(
                request.languagePair(),
                normalizeLookupTerm(request.requestedTerm())
        ));
        Optional<DictionaryEntryDto> result = Optional.ofNullable(fixture)
                .map(value -> value.toEntry(request.requestedTerm()));
        log.info(
                "Dictionary lookup completed providerId={} providerVersion={} sourceLanguage={} "
                        + "definitionLanguage={} termCharacters={} found={}",
                PROVIDER_ID,
                PROVIDER_VERSION,
                request.languagePair().sourceLanguage(),
                request.languagePair().definitionLanguage(),
                request.requestedTerm().codePointCount(0, request.requestedTerm().length()),
                result.isPresent()
        );
        return result;
    }

    private Map<LookupKey, Fixture> indexFixtures(List<Fixture> fixtures) {
        LinkedHashMap<LookupKey, Fixture> indexed = new LinkedHashMap<>();
        for (Fixture fixture : fixtures) {
            // Index lemma and every inflected form to make fake morphology behave like
            // the real provider contract the LOOKUP worker will consume.
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            aliases.add(fixture.lemma());
            aliases.addAll(fixture.forms());
            for (String alias : aliases) {
                LookupKey key = new LookupKey(fixture.languagePair(), normalizeLookupTerm(alias));
                Fixture duplicate = indexed.putIfAbsent(key, fixture);
                if (duplicate != null && duplicate != fixture) {
                    throw new IllegalStateException("Duplicate fake dictionary fixture alias: " + alias);
                }
            }
        }
        return Map.copyOf(indexed);
    }

    private String normalizeLookupTerm(String value) {
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private List<Fixture> fixtures() {
        return List.of(
                fixture(
                        "es", "en", "vivir",
                        List.of("vivir", "vivo", "vive", "vivimos", "vivió"),
                        List.of(
                                sense("verb", "to live; to be alive", "es-en-vivir-verb-1"),
                                sense("verb", "to reside in a place", "es-en-vivir-verb-2")
                        )
                ),
                fixture(
                        "en", "es", "live",
                        List.of("live", "lives", "lived", "living"),
                        List.of(
                                sense("verb", "vivir", "en-es-live-verb-1"),
                                sense("adjective", "en vivo", "en-es-live-adjective-1")
                        )
                ),
                fixture(
                        "es", "es", "vivir",
                        List.of("vivir", "vivo", "vive", "vivimos", "vivió"),
                        List.of(sense(
                                "verb",
                                "tener vida o pasar la vida de una manera determinada",
                                "es-es-vivir-verb-1"
                        ))
                ),
                fixture(
                        "en", "en", "live",
                        List.of("live", "lives", "lived", "living"),
                        List.of(
                                sense("verb", "to be alive", "en-en-live-verb-1"),
                                sense("adjective", "happening or broadcast at the present time", "en-en-live-adjective-1")
                        )
                ),
                fixture(
                        "fr", "en", "vivre",
                        List.of("vivre", "vis", "vit", "vivons", "vivent"),
                        List.of(sense("verb", "to live", "fr-en-vivre-verb-1"))
                )
        );
    }

    private Fixture fixture(
            String sourceLanguage,
            String definitionLanguage,
            String lemma,
            List<String> forms,
            List<DictionarySenseDto> senses
    ) {
        return new Fixture(new LanguagePairDto(sourceLanguage, definitionLanguage), lemma, forms, senses);
    }

    private DictionarySenseDto sense(String partOfSpeech, String definition, String reference) {
        return new DictionarySenseDto(partOfSpeech, definition, "fake:" + reference);
    }

    private record LookupKey(LanguagePairDto languagePair, String normalizedTerm) {
    }

    private record Fixture(
            LanguagePairDto languagePair,
            String lemma,
            List<String> forms,
            List<DictionarySenseDto> senses
    ) {
        private Fixture {
            forms = List.copyOf(forms);
            senses = List.copyOf(senses);
        }

        private DictionaryEntryDto toEntry(String requestedTerm) {
            String fixtureId = languagePair.sourceLanguage() + "-"
                    + languagePair.definitionLanguage() + "-" + lemma;
            LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
            metadata.put("fixtureId", fixtureId);
            return new DictionaryEntryDto(
                    requestedTerm,
                    lemma,
                    new ArrayList<>(forms),
                    senses,
                    languagePair,
                    new DictionaryProvenanceDto(
                            PROVIDER_ID,
                            PROVIDER_VERSION,
                            "fake:" + fixtureId,
                            "",
                            "INTERNAL-TEST-DATA",
                            "Deterministic WhatsAnki fixture; not a production dictionary definition.",
                            metadata
                    )
            );
        }
    }
}
