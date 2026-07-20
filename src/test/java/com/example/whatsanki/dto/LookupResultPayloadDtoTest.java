package com.example.whatsanki.dto;

import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LookupResultPayloadDtoTest {

    @Test
    void storesConsecutiveContextsAndPreservesFormatOwnedLocationMetadata() {
        DictionaryEntryDto entry = entry();
        ContextExampleDto pdf = context(
                SourceDocumentEntity.Kind.PDF,
                Map.of("pageNumber", 7)
        );
        ContextExampleDto futureTimedSource = context(
                SourceDocumentEntity.Kind.PASTED_TEXT,
                Map.of("provider", "YOUTUBE", "videoId", "abc123", "startMs", 12_000, "endMs", 16_000)
        );

        Map<String, Object> payload = LookupResultPayloadDto.create(
                entry, List.of(pdf, futureTimedSource)
        ).toMap();

        assertThat(payload).containsEntry("schemaVersion", 1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contexts = (List<Map<String, Object>>) payload.get("contexts");
        assertThat(contexts).extracting(context -> context.get("number")).containsExactly(1, 2);
        assertThat(map(contexts.get(0).get("location"))).containsEntry("pageNumber", 7);
        assertThat(map(contexts.get(1).get("location")))
                .containsEntry("videoId", "abc123")
                .containsEntry("startMs", 12_000)
                .containsEntry("endMs", 16_000);
        assertThat(map(payload.get("dictionary")))
                .containsEntry("sourceLanguage", "es")
                .containsEntry("definitionLanguage", "en");
    }

    @Test
    void rejectsMissingOrNonConsecutiveContexts() {
        assertThatThrownBy(() -> new LookupResultPayloadDto(1, entry(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LookupResultPayloadDto(
                1,
                entry(),
                List.of(NumberedContextExampleDto.from(2, context(
                        SourceDocumentEntity.Kind.PDF, Map.of("pageNumber", 1)
                )))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consecutively");
    }

    private DictionaryEntryDto entry() {
        return new DictionaryEntryDto(
                "vivir",
                "vivir",
                List.of("vivo"),
                List.of(new DictionarySenseDto("verb", "to live", "fake:sense")),
                new LanguagePairDto("es", "en"),
                new DictionaryProvenanceDto(
                        "fake", "v1", "fake:vivir", "", "TEST", "Test data", Map.of()
                )
        );
    }

    private ContextExampleDto context(SourceDocumentEntity.Kind kind, Map<String, Object> location) {
        return new ContextExampleDto(
                UUID.randomUUID(),
                SourceId.random(),
                kind,
                "Source",
                new LanguageTag("es"),
                0,
                "Quiero vivir aquí.",
                location
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
