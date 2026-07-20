package com.example.whatsanki.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable field contract for the packaged {@code WhatsAnki Context v1} Anki
 * note type. The explicit order is part of the genanki model contract.
 */
public record AnkiCardFieldsDto(
        int schemaVersion,
        String noteTypeName,
        String templateVersion,
        String term,
        String contextBefore,
        String contextTarget,
        String contextAfter,
        String contextTranslation,
        String definition,
        String definitionDetail,
        String pronunciation,
        String partOfSpeech,
        String gender,
        String grammaticalNumber,
        String wordFamily,
        String sourceName,
        String sourceLocator,
        String languagePair,
        String sentenceAudio,
        String wordAudio,
        String video,
        String textRecognition,
        String videoRecognition,
        String recall
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String NOTE_TYPE_NAME = "WhatsAnki Context v1";
    public static final String TEMPLATE_VERSION = "whatsanki-context-v1";
    public static final List<String> FIELD_ORDER = List.of(
            "Term", "ContextBefore", "ContextTarget", "ContextAfter", "ContextTranslation",
            "Definition", "DefinitionDetail", "Pronunciation", "PartOfSpeech", "Gender",
            "Number", "WordFamily", "SourceName", "SourceLocator", "LanguagePair",
            "SentenceAudio", "WordAudio", "Video", "TextRecognition", "VideoRecognition", "Recall"
    );

    public AnkiCardFieldsDto {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Anki card field schema version");
        }
        if (!NOTE_TYPE_NAME.equals(noteTypeName)) {
            throw new IllegalArgumentException("Unsupported Anki note type");
        }
        if (!TEMPLATE_VERSION.equals(templateVersion)) {
            throw new IllegalArgumentException("Unsupported Anki template version");
        }
        term = required(term, "term");
        contextBefore = optional(contextBefore);
        contextTarget = required(contextTarget, "contextTarget");
        contextAfter = optional(contextAfter);
        contextTranslation = optional(contextTranslation);
        definition = required(definition, "definition");
        definitionDetail = optional(definitionDetail);
        pronunciation = optional(pronunciation);
        partOfSpeech = optional(partOfSpeech);
        gender = optional(gender);
        grammaticalNumber = optional(grammaticalNumber);
        wordFamily = optional(wordFamily);
        sourceName = optional(sourceName);
        sourceLocator = optional(sourceLocator);
        languagePair = optional(languagePair);
        sentenceAudio = optional(sentenceAudio);
        wordAudio = optional(wordAudio);
        video = optional(video);
        textRecognition = optional(textRecognition);
        videoRecognition = optional(videoRecognition);
        recall = optional(recall);
    }

    /** Returns JSON-native fields with the exact spelling and order required by Anki. */
    public Map<String, String> toFieldMap() {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("Term", term);
        fields.put("ContextBefore", contextBefore);
        fields.put("ContextTarget", contextTarget);
        fields.put("ContextAfter", contextAfter);
        fields.put("ContextTranslation", contextTranslation);
        fields.put("Definition", definition);
        fields.put("DefinitionDetail", definitionDetail);
        fields.put("Pronunciation", pronunciation);
        fields.put("PartOfSpeech", partOfSpeech);
        fields.put("Gender", gender);
        fields.put("Number", grammaticalNumber);
        fields.put("WordFamily", wordFamily);
        fields.put("SourceName", sourceName);
        fields.put("SourceLocator", sourceLocator);
        fields.put("LanguagePair", languagePair);
        fields.put("SentenceAudio", sentenceAudio);
        fields.put("WordAudio", wordAudio);
        fields.put("Video", video);
        fields.put("TextRecognition", textRecognition);
        fields.put("VideoRecognition", videoRecognition);
        fields.put("Recall", recall);
        // Map.copyOf does not promise insertion order; genanki receives fields in
        // this declared order, so retain the LinkedHashMap iteration contract.
        return Collections.unmodifiableMap(fields);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    private static String optional(String value) {
        return Objects.requireNonNullElse(value, "");
    }
}
