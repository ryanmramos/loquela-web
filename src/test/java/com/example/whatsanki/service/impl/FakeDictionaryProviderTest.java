package com.example.whatsanki.service.impl;

import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionaryLookupRequestDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.service.exception.UnsupportedDictionaryLanguagePairException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeDictionaryProviderTest {

    private FakeDictionaryProvider provider;

    @BeforeEach
    void setUp() {
        provider = new FakeDictionaryProvider();
    }

    @Test
    void advertisesStableIdentityAndMultilingualCapabilities() {
        assertThat(provider.providerId()).isEqualTo("fake");
        assertThat(provider.providerVersion()).isEqualTo("fixture-v1");
        assertThat(provider.supportedPairs()).containsExactly(
                new LanguagePairDto("es", "en"),
                new LanguagePairDto("en", "es"),
                new LanguagePairDto("es", "es"),
                new LanguagePairDto("en", "en"),
                new LanguagePairDto("fr", "en")
        );
        assertThatThrownBy(() -> provider.supportedPairs().add(new LanguagePairDto("de", "en")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void returnsTheSameEntryForAnInflectedFormDeterministically() {
        DictionaryLookupRequestDto request = new DictionaryLookupRequestDto(
                " VIVE ",
                new LanguagePairDto("ES", "EN")
        );

        DictionaryEntryDto first = provider.lookup(request).orElseThrow();
        DictionaryEntryDto second = provider.lookup(request).orElseThrow();

        assertThat(first).isEqualTo(second);
        assertThat(first.requestedTerm()).isEqualTo("VIVE");
        assertThat(first.lemma()).isEqualTo("vivir");
        assertThat(first.forms()).containsExactly("vivir", "vivo", "vive", "vivimos", "vivió");
        assertThat(first.senses()).extracting(sense -> sense.definition())
                .containsExactly("to live; to be alive", "to reside in a place");
        assertThat(first.searchTerms()).containsExactly("VIVE", "vivir", "vivo", "vive", "vivimos", "vivió");
        assertThat(first.provenance().providerId()).isEqualTo("fake");
        assertThat(first.provenance().providerVersion()).isEqualTo("fixture-v1");
    }

    @Test
    void keepsBilingualAndMonolingualDefinitionsPairSpecific() {
        DictionaryEntryDto bilingual = lookup("live", "en", "es");
        DictionaryEntryDto monolingual = lookup("live", "en", "en");

        assertThat(bilingual.senses()).extracting(sense -> sense.definition())
                .containsExactly("vivir", "en vivo");
        assertThat(monolingual.senses()).extracting(sense -> sense.definition())
                .containsExactly("to be alive", "happening or broadcast at the present time");
        assertThat(bilingual.senses()).extracting(sense -> sense.partOfSpeech())
                .containsExactly("verb", "adjective");
    }

    @Test
    void supportsASecondNonSpanishSourceLanguage() {
        DictionaryEntryDto result = lookup("vit", "fr", "en");

        assertThat(result.lemma()).isEqualTo("vivre");
        assertThat(result.forms()).contains("vis", "vit", "vivons", "vivent");
        assertThat(result.senses()).singleElement()
                .extracting(sense -> sense.definition())
                .isEqualTo("to live");
    }

    @Test
    void returnsEmptyForAMissingTermInASupportedPair() {
        assertThat(provider.lookup(new DictionaryLookupRequestDto(
                "not-in-fixtures",
                new LanguagePairDto("es", "en")
        ))).isEmpty();
    }

    @Test
    void rejectsUnsupportedPairsWithoutAttemptingFallback() {
        LanguagePairDto unsupported = new LanguagePairDto("de", "en");

        assertThatThrownBy(() -> provider.lookup(new DictionaryLookupRequestDto("leben", unsupported)))
                .isInstanceOfSatisfying(UnsupportedDictionaryLanguagePairException.class, exception -> {
                    assertThat(exception.getProviderId()).isEqualTo("fake");
                    assertThat(exception.getLanguagePair()).isEqualTo(unsupported);
                });
    }

    private DictionaryEntryDto lookup(String term, String sourceLanguage, String definitionLanguage) {
        return provider.lookup(new DictionaryLookupRequestDto(
                term,
                new LanguagePairDto(sourceLanguage, definitionLanguage)
        )).orElseThrow();
    }
}
