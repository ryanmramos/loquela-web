package com.example.whatsanki.service.dictionary;

import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionaryLookupRequestDto;
import com.example.whatsanki.dto.DictionaryProvenanceDto;
import com.example.whatsanki.dto.DictionarySenseDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.service.exception.DictionaryProviderException;
import com.example.whatsanki.service.exception.UnsupportedDictionaryLanguagePairException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutedDictionaryProviderTest {

    private static final LanguagePairDto SPANISH = new LanguagePairDto("es", "es");

    @Test
    void usesTheSpecialistBeforeTheGenericFallbackAndPreservesItsProvenance() {
        DictionaryProvider specialist = provider("rae", Optional.of(entry("rae")));
        DictionaryProvider generic = provider("wiktionary", Optional.of(entry("wiktionary")));
        RoutedDictionaryProvider routed = new RoutedDictionaryProvider(
                List.of(generic), Map.of("es:es", List.of(specialist, generic))
        );

        DictionaryEntryDto result = routed.lookup(new DictionaryLookupRequestDto("vivir", SPANISH)).orElseThrow();

        assertThat(result.provenance().providerId()).isEqualTo("rae");
    }

    @Test
    void fallsBackOnlyWhenTheSpecialistHasNoDefinition() {
        DictionaryProvider specialist = provider("rae", Optional.empty());
        DictionaryProvider generic = provider("wiktionary", Optional.of(entry("wiktionary")));
        RoutedDictionaryProvider routed = new RoutedDictionaryProvider(
                List.of(generic), Map.of("es:es", List.of(specialist, generic))
        );

        assertThat(routed.lookup(new DictionaryLookupRequestDto("vivir", SPANISH)).orElseThrow()
                .provenance().providerId()).isEqualTo("wiktionary");
    }

    @Test
    void doesNotHideSpecialistThrottleBehindAGenericResult() {
        DictionaryProvider specialist = failingProvider("rae", DictionaryProviderException.FailureKind.RATE_LIMITED);
        DictionaryProvider generic = provider("wiktionary", Optional.of(entry("wiktionary")));
        RoutedDictionaryProvider routed = new RoutedDictionaryProvider(
                List.of(generic), Map.of("es:es", List.of(specialist, generic))
        );

        assertThatThrownBy(() -> routed.lookup(new DictionaryLookupRequestDto("vivir", SPANISH)))
                .isInstanceOfSatisfying(DictionaryProviderException.class, exception ->
                        assertThat(exception.providerId()).isEqualTo("rae")
                );
    }

    @Test
    void rejectsPairsForWhichNoConfiguredProviderHasACapability() {
        RoutedDictionaryProvider routed = new RoutedDictionaryProvider(
                List.of(provider("wiktionary", Optional.empty())), Map.of()
        );

        assertThatThrownBy(() -> routed.lookup(new DictionaryLookupRequestDto("leben", new LanguagePairDto("de", "en"))))
                .isInstanceOf(UnsupportedDictionaryLanguagePairException.class);
    }

    @Test
    void skipsTheSpanishMonolingualSpecialistForSpanishToEnglish() {
        AtomicInteger specialistCalls = new AtomicInteger();
        DictionaryProvider specialist = new DictionaryProvider() {
            @Override public String providerId() { return "rae"; }
            @Override public String providerVersion() { return "test-v1"; }
            @Override public List<LanguagePairDto> supportedPairs() { return List.of(SPANISH); }
            @Override public Optional<DictionaryEntryDto> lookup(DictionaryLookupRequestDto request) {
                specialistCalls.incrementAndGet();
                return Optional.of(entry("rae"));
            }
        };
        LanguagePairDto spanishToEnglish = new LanguagePairDto("es", "en");
        DictionaryProvider generic = provider(
                "wiktionary",
                spanishToEnglish,
                Optional.of(entry("wiktionary", spanishToEnglish))
        );
        RoutedDictionaryProvider routed = new RoutedDictionaryProvider(
                List.of(specialist, generic), Map.of()
        );

        DictionaryEntryDto result = routed.lookup(
                new DictionaryLookupRequestDto("vivir", spanishToEnglish)
        ).orElseThrow();

        assertThat(result.provenance().providerId()).isEqualTo("wiktionary");
        assertThat(specialistCalls).hasValue(0);
    }

    private DictionaryProvider provider(String id, Optional<DictionaryEntryDto> result) {
        return provider(id, SPANISH, result);
    }

    private DictionaryProvider provider(
            String id,
            LanguagePairDto supportedPair,
            Optional<DictionaryEntryDto> result
    ) {
        return new DictionaryProvider() {
            @Override public String providerId() { return id; }
            @Override public String providerVersion() { return "test-v1"; }
            @Override public List<LanguagePairDto> supportedPairs() { return List.of(supportedPair); }
            @Override public Optional<DictionaryEntryDto> lookup(DictionaryLookupRequestDto request) { return result; }
        };
    }

    private DictionaryProvider failingProvider(String id, DictionaryProviderException.FailureKind kind) {
        return new DictionaryProvider() {
            @Override public String providerId() { return id; }
            @Override public String providerVersion() { return "test-v1"; }
            @Override public List<LanguagePairDto> supportedPairs() { return List.of(SPANISH); }
            @Override public Optional<DictionaryEntryDto> lookup(DictionaryLookupRequestDto request) {
                throw new DictionaryProviderException(id, kind, null);
            }
        };
    }

    private DictionaryEntryDto entry(String providerId) {
        return entry(providerId, SPANISH);
    }

    private DictionaryEntryDto entry(String providerId, LanguagePairDto pair) {
        return new DictionaryEntryDto(
                "vivir", "vivir", List.of("vivir"),
                List.of(new DictionarySenseDto("verb", "Tener vida.", providerId + ":1")), pair,
                new DictionaryProvenanceDto(providerId, "test-v1", providerId + ":vivir", "", "TEST", "Test", Map.of())
        );
    }
}
