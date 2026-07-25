package com.example.whatsanki.service.lookup;

import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.dto.ContextExampleDto;
import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionaryProvenanceDto;
import com.example.whatsanki.dto.DictionarySenseDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LookupResultPayloadDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.OutboundDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LookupResultMessageFormatterImplTest {
    private WhatsAnkiConfigurationDto configuration;
    private LookupResultMessageFormatterImpl formatter;

    @BeforeEach
    void setUp() {
        configuration = new WhatsAnkiConfigurationDto();
        formatter = new LookupResultMessageFormatterImpl(configuration);
    }

    @Test
    void formatsDefinitionsNumberedContextsAndSourceNeutralLocations() {
        Map<String, Object> payload = LookupResultPayloadDto.create(
                entry(),
                List.of(
                        context("PDF book", Map.of("pageNumber", 12)),
                        context("Video", Map.of(
                                "videoId", "dQw4w9WgXcQ",
                                "startMs", 65_900,
                                "playbackStartMs", 62_900
                        ))
                )
        ).toMap();

        String message = String.join("\n", formatter.format(payload));

        assertThat(message)
                .contains("Lookup: vivir (es → en)")
                .contains("Definition 1 [verb]: to live")
                .contains("1. Quiero *vivir* aquí.")
                .contains("— PDF book, p. 12")
                .contains("2. Quiero *vivir* aquí.")
                .contains("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=62s")
                .contains("Dictionary attribution: Test data");
    }

    @Test
    void boldsRequestedLemmaAndFormsWithoutMatchingInsideOtherWords() {
        Map<String, Object> payload = LookupResultPayloadDto.create(
                entry(),
                List.of(context(
                        "PDF book",
                        Map.of("pageNumber", 3),
                        "Vivo para vivir; revivo recuerdos sobre VIVIR."
                ))
        ).toMap();

        String message = String.join("\n", formatter.format(payload));

        assertThat(message)
                .contains("1. *Vivo* para *vivir*; revivo recuerdos sobre *VIVIR*.")
                .doesNotContain("re*vivo*");
    }

    @Test
    void splitsLongResultsWithinConfiguredCodePointLimit() {
        configuration.getOutbound().setMaxMessageCharacters(200);
        Map<String, Object> payload = LookupResultPayloadDto.create(
                entry(),
                List.of(context("Long source", Map.of("pageNumber", 1), "x".repeat(500)))
        ).toMap();

        List<String> parts = formatter.format(payload);

        assertThat(parts).hasSizeGreaterThan(1);
        assertThat(parts).allSatisfy(part -> assertThat(
                part.codePointCount(0, part.length())
        ).isLessThanOrEqualTo(200));
        assertThat(parts.get(0)).startsWith("Lookup result (1/");
    }

    @Test
    void rejectsMalformedStoredSnapshotWithSafePermanentCode() {
        assertThatThrownBy(() -> formatter.format(Map.of("schemaVersion", 1)))
                .isInstanceOfSatisfying(OutboundDeliveryException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo("LOOKUP_RESULT_INVALID");
                    assertThat(exception.failureKind().name()).isEqualTo("PERMANENT");
                });
    }

    private DictionaryEntryDto entry() {
        return new DictionaryEntryDto(
                "vivir", "vivir", List.of("vivo"),
                List.of(new DictionarySenseDto("verb", "to live", "fake:sense")),
                new LanguagePairDto("es", "en"),
                new DictionaryProvenanceDto(
                        "fake", "v1", "fake:vivir", "", "TEST", "Test data", Map.of()
                )
        );
    }

    private ContextExampleDto context(String source, Map<String, Object> location) {
        return context(source, location, "Quiero vivir aquí.");
    }

    private ContextExampleDto context(
            String source,
            Map<String, Object> location,
            String displayText
    ) {
        return new ContextExampleDto(
                UUID.randomUUID(), SourceId.random(), SourceDocumentEntity.Kind.PDF,
                source, new LanguageTag("es"), 0, displayText, location
        );
    }
}
