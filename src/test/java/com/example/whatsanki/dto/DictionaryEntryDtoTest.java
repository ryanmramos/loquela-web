package com.example.whatsanki.dto;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DictionaryEntryDtoTest {

    @Test
    void createsImmutableDeduplicatedSearchTermsInStableOrder() {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("revisionId", "42");
        DictionaryEntryDto entry = new DictionaryEntryDto(
                "vive",
                "vivir",
                List.of("vivir", "vive", "vivo", "vive"),
                List.of(new DictionarySenseDto("VERB", "to live", "fixture:sense-1")),
                new LanguagePairDto("ES", "EN"),
                new DictionaryProvenanceDto(
                        "fixture",
                        "v1",
                        "fixture:vivir",
                        "https://dictionary.example/vivir",
                        "TEST-LICENSE",
                        "Test attribution",
                        metadata
                )
        );

        metadata.put("lateMutation", "not copied");

        assertThat(entry.languagePair()).isEqualTo(new LanguagePairDto("es", "en"));
        assertThat(entry.senses()).singleElement()
                .extracting(DictionarySenseDto::partOfSpeech)
                .isEqualTo("verb");
        assertThat(entry.forms()).containsExactly("vivir", "vive", "vivo");
        assertThat(entry.searchTerms()).containsExactly("vive", "vivir", "vivo");
        assertThat(entry.provenance().metadata()).containsExactlyEntriesOf(
                java.util.Map.of("revisionId", "42")
        );
        assertThatThrownBy(() -> entry.forms().add("vivimos"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> entry.provenance().metadata().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsIncompleteProviderResults() {
        DictionaryProvenanceDto provenance = new DictionaryProvenanceDto(
                "fixture", "v1", "fixture:entry", "", "TEST", "Test attribution", new LinkedHashMap<>()
        );

        assertThatThrownBy(() -> new DictionaryEntryDto(
                "term",
                "lemma",
                List.of(),
                List.of(),
                new LanguagePairDto("es", "en"),
                provenance
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("senses");
    }
}
