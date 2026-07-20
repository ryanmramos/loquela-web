package com.example.whatsanki.service.util;

import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.dto.AnkiCardFieldsDto;
import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionaryProvenanceDto;
import com.example.whatsanki.dto.DictionarySenseDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.NumberedContextExampleDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnkiCardFieldMapperTest {

    private final AnkiCardFieldMapper mapper = new AnkiCardFieldMapper();

    @Test
    void createsExactOrderedTemplateFieldsFromTheEncounteredInflectedForm() {
        AnkiCardFieldsDto fields = mapper.map(entry(), context("Ella vive aquí.", Map.of("pageNumber", 8)));

        assertThat(fields.noteTypeName()).isEqualTo("WhatsAnki Context v1");
        assertThat(fields.templateVersion()).isEqualTo("whatsanki-context-v1");
        assertThat(fields.contextBefore()).isEqualTo("Ella ");
        assertThat(fields.contextTarget()).isEqualTo("vive");
        assertThat(fields.contextAfter()).isEqualTo(" aquí.");
        assertThat(fields.definition()).isEqualTo("to live");
        assertThat(fields.definitionDetail()).isEqualTo("noun: life");
        assertThat(fields.partOfSpeech()).isEqualTo("verb");
        assertThat(fields.sourceLocator()).isEqualTo("p. 8");
        assertThat(fields.textRecognition()).isEqualTo("1");
        assertThat(fields.videoRecognition()).isEmpty();
        assertThat(fields.toFieldMap().keySet()).containsExactlyElementsOf(AnkiCardFieldsDto.FIELD_ORDER);
    }

    @Test
    void formatsFutureTimedVideoContextWithoutASeparateSourceSpecificCardShape() {
        AnkiCardFieldsDto fields = mapper.map(entry(), context("Quiero vivir aquí.", Map.of("startMs", 62_000)));

        assertThat(fields.sourceLocator()).isEqualTo("1:02");
    }

    @Test
    void rejectsAContextThatCannotSafelySupplyTheHighlightedTarget() {
        assertThatThrownBy(() -> mapper.map(entry(), context("Ella estudia aquí.", Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not contain");
    }

    private DictionaryEntryDto entry() {
        return new DictionaryEntryDto(
                "vivir", "vivir", List.of("vive"),
                List.of(new DictionarySenseDto("verb", "to live", "fixture:1"),
                        new DictionarySenseDto("noun", "life", "fixture:2")),
                new LanguagePairDto("es", "en"),
                new DictionaryProvenanceDto("fixture", "v1", "entry", "", "TEST", "Test", Map.of())
        );
    }

    private NumberedContextExampleDto context(String text, Map<String, Object> location) {
        return new NumberedContextExampleDto(
                1, UUID.randomUUID(), UUID.randomUUID(), SourceDocumentEntity.Kind.PDF,
                "Book", "es", 0, text, location
        );
    }
}
