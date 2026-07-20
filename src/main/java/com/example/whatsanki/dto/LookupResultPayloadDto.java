package com.example.whatsanki.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Versioned JSON snapshot stored in {@code lookup_session.result_payload}. */
public record LookupResultPayloadDto(
        int schemaVersion,
        DictionaryEntryDto dictionaryEntry,
        List<NumberedContextExampleDto> contexts
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public LookupResultPayloadDto {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported lookup result schema version");
        }
        Objects.requireNonNull(dictionaryEntry, "dictionaryEntry must not be null");
        if (contexts == null || contexts.isEmpty() || contexts.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("contexts must contain at least one non-null result");
        }
        contexts = List.copyOf(contexts);
        for (int index = 0; index < contexts.size(); index++) {
            if (contexts.get(index).number() != index + 1) {
                throw new IllegalArgumentException("contexts must be numbered consecutively from one");
            }
        }
    }

    /**
     * Build only JSON-native values. Keeping this mapping explicit makes the persisted
     * SAVE contract independent of Jackson modules or Java record serialization details.
     */
    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", schemaVersion);
        payload.put("dictionary", dictionaryMap());
        payload.put("contexts", contexts.stream().map(this::contextMap).toList());
        return Map.copyOf(payload);
    }

    private Map<String, Object> dictionaryMap() {
        LinkedHashMap<String, Object> dictionary = new LinkedHashMap<>();
        dictionary.put("requestedTerm", dictionaryEntry.requestedTerm());
        dictionary.put("lemma", dictionaryEntry.lemma());
        dictionary.put("forms", dictionaryEntry.forms());
        dictionary.put("sourceLanguage", dictionaryEntry.languagePair().sourceLanguage());
        dictionary.put("definitionLanguage", dictionaryEntry.languagePair().definitionLanguage());
        dictionary.put("senses", dictionaryEntry.senses().stream().map(sense -> Map.of(
                "partOfSpeech", sense.partOfSpeech(),
                "definition", sense.definition(),
                "providerReference", sense.providerReference()
        )).toList());

        DictionaryProvenanceDto provenance = dictionaryEntry.provenance();
        LinkedHashMap<String, Object> provenanceMap = new LinkedHashMap<>();
        provenanceMap.put("providerId", provenance.providerId());
        provenanceMap.put("providerVersion", provenance.providerVersion());
        provenanceMap.put("sourceReference", provenance.sourceReference());
        provenanceMap.put("sourceUrl", provenance.sourceUrl());
        provenanceMap.put("licenseIdentifier", provenance.licenseIdentifier());
        provenanceMap.put("attributionText", provenance.attributionText());
        provenanceMap.put("metadata", provenance.metadata());
        dictionary.put("provenance", Map.copyOf(provenanceMap));
        return Map.copyOf(dictionary);
    }

    private Map<String, Object> contextMap(NumberedContextExampleDto context) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("number", context.number());
        value.put("passageId", context.passageId().toString());
        value.put("sourceId", context.sourceId().toString());
        value.put("sourceKind", context.sourceKind().name());
        value.put("sourceDisplayName", context.sourceDisplayName());
        value.put("sourceLanguage", context.sourceLanguage());
        value.put("ordinal", context.ordinal());
        value.put("displayText", context.displayText());
        value.put("location", new LinkedHashMap<>(context.location()));
        return Map.copyOf(value);
    }

    public static LookupResultPayloadDto create(
            DictionaryEntryDto dictionaryEntry,
            List<ContextExampleDto> contexts
    ) {
        Objects.requireNonNull(contexts, "contexts must not be null");
        List<NumberedContextExampleDto> numbered = new ArrayList<>(contexts.size());
        for (int index = 0; index < contexts.size(); index++) {
            numbered.add(NumberedContextExampleDto.from(index + 1, contexts.get(index)));
        }
        return new LookupResultPayloadDto(CURRENT_SCHEMA_VERSION, dictionaryEntry, numbered);
    }
}
