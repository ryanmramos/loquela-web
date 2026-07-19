package com.example.whatsanki.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanguagePairDtoTest {

    @Test
    void canonicalizesBothBcp47LanguageTags() {
        LanguagePairDto pair = new LanguagePairDto("ES-latn-us", "en-us");

        assertThat(pair.sourceLanguage()).isEqualTo("es-Latn-US");
        assertThat(pair.definitionLanguage()).isEqualTo("en-US");
    }

    @Test
    void rejectsAValueThatDoesNotIdentifyALanguage() {
        assertThatThrownBy(() -> new LanguagePairDto("und", "en"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identify a language");
    }
}
