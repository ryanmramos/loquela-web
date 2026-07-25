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
    void createsVideoRecognitionCardAtContextLeadTimestamp() {
        NumberedContextExampleDto video = new NumberedContextExampleDto(
                1, UUID.randomUUID(), UUID.randomUUID(), SourceDocumentEntity.Kind.YOUTUBE_VIDEO,
                "A lesson", "es", 0, "Quiero vivir aquí.", Map.of(
                        "videoId", "dQw4w9WgXcQ", "startMs", 65_900,
                        "playbackStartMs", 62_900, "channelName", "Spanish School"
                )
        );

        AnkiCardFieldsDto fields = mapper.map(entry(), video);

        assertThat(fields.video()).isEqualTo(
                "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?start=62&rel=0"
        );
        assertThat(fields.sourceName()).isEqualTo("YouTube · Spanish School");
        assertThat(fields.sourceLocator()).isEqualTo("1:05");
        assertThat(fields.textRecognition()).isEmpty();
        assertThat(fields.videoRecognition()).isEqualTo("1");
    }

    @Test
    void rejectsVideoContextWithoutTrustedPlaybackLocation() {
        NumberedContextExampleDto video = new NumberedContextExampleDto(
                1, UUID.randomUUID(), UUID.randomUUID(), SourceDocumentEntity.Kind.YOUTUBE_VIDEO,
                "Video", "es", 0, "Quiero vivir aquí.", Map.of("videoId", "not-valid")
        );

        assertThatThrownBy(() -> mapper.map(entry(), video))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("video id");
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
