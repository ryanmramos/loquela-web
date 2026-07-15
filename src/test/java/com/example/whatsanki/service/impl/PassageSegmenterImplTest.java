package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PassageSegmenterImplTest {

    @Test
    void preservesOrderAndParagraphLocationsWhileGroupingSentences() {
        WhatsAnkiConfigurationDto configuration = configuration(80, 20, 2);
        PassageSegmenterImpl segmenter = new PassageSegmenterImpl(configuration);
        String text = "Uno aquí. Dos allí. Tres más.\n\nCuatro final.";

        var passages = segmenter.segment(text, new LanguageTag("es"));

        assertThat(passages).extracting(passage -> passage.displayText())
                .containsExactly("Uno aquí. Dos allí.", "Tres más.", "Cuatro final.");
        assertThat(passages).extracting(passage -> passage.location().get("paragraphIndex"))
                .containsExactly(0, 0, 1);
        assertThat(passages).extracting(passage -> passage.location().get("startCharacter"))
                .containsExactly(0, 20, 31);
        assertThat(passages).allSatisfy(passage -> assertThat(passage.tokenCount()).isPositive());
    }

    @Test
    void splitsLongSentencesAtConfiguredCharacterAndTokenLimits() {
        WhatsAnkiConfigurationDto configuration = configuration(12, 2, 3);
        PassageSegmenterImpl segmenter = new PassageSegmenterImpl(configuration);

        var passages = segmenter.segment("alpha beta gamma delta", new LanguageTag("en"));

        assertThat(passages).extracting(passage -> passage.displayText())
                .containsExactly("alpha beta", "gamma delta");
        assertThat(passages).allSatisfy(passage -> {
            assertThat(passage.displayText().codePointCount(0, passage.displayText().length()))
                    .isLessThanOrEqualTo(12);
            assertThat(passage.tokenCount()).isLessThanOrEqualTo(2);
        });
    }

    @Test
    void storesUnicodeCodePointOffsetsInsteadOfUtf16Indexes() {
        WhatsAnkiConfigurationDto configuration = configuration(80, 20, 1);
        PassageSegmenterImpl segmenter = new PassageSegmenterImpl(configuration);

        var passages = segmenter.segment("😀 Hola. Adiós.", new LanguageTag("es"));

        assertThat(passages).extracting(passage -> passage.displayText())
                .containsExactly("😀 Hola.", "Adiós.");
        assertThat(passages).extracting(passage -> passage.location().get("startCharacter"))
                .containsExactly(0, 8);
        assertThat(passages).extracting(passage -> passage.location().get("endCharacter"))
                .containsExactly(7, 14);
    }

    private WhatsAnkiConfigurationDto configuration(int characters, int tokens, int sentences) {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.getIngestion().setMaxPassageCharacters(characters);
        configuration.getIngestion().setMaxPassageTokens(tokens);
        configuration.getIngestion().setMaxPassageSentences(sentences);
        return configuration;
    }
}
