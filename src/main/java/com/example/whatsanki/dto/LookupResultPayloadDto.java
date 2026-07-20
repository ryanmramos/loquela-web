package com.example.whatsanki.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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

    /**
     * Rebuild the typed snapshot from JSON-native database values. SAVE uses this
     * explicit codec so persisted snapshots remain stable across Jackson changes.
     */
    public static LookupResultPayloadDto fromMap(Map<String, Object> payload) {
        Map<String, Object> root = requiredMap(payload, "lookup result");
        int schemaVersion = requiredInt(root.get("schemaVersion"), "schemaVersion");
        Map<String, Object> dictionary = requiredMap(root.get("dictionary"), "dictionary");

        LanguagePairDto languagePair = new LanguagePairDto(
                requiredString(dictionary.get("sourceLanguage"), "sourceLanguage"),
                requiredString(dictionary.get("definitionLanguage"), "definitionLanguage")
        );
        DictionaryProvenanceDto provenance = provenance(
                requiredMap(dictionary.get("provenance"), "provenance")
        );
        DictionaryEntryDto entry = new DictionaryEntryDto(
                requiredString(dictionary.get("requestedTerm"), "requestedTerm"),
                requiredString(dictionary.get("lemma"), "lemma"),
                stringList(dictionary.get("forms"), "forms"),
                senses(dictionary.get("senses")),
                languagePair,
                provenance
        );

        Object rawContexts = root.get("contexts");
        if (!(rawContexts instanceof List<?> contextValues)) {
            throw new IllegalArgumentException("contexts must be a list");
        }
        List<NumberedContextExampleDto> contexts = new ArrayList<>(contextValues.size());
        for (Object rawContext : contextValues) {
            Map<String, Object> context = requiredMap(rawContext, "context");
            contexts.add(new NumberedContextExampleDto(
                    requiredInt(context.get("number"), "number"),
                    UUID.fromString(requiredString(context.get("passageId"), "passageId")),
                    UUID.fromString(requiredString(context.get("sourceId"), "sourceId")),
                    com.example.whatsanki.domain.SourceDocumentEntity.Kind.valueOf(
                            requiredString(context.get("sourceKind"), "sourceKind")
                    ),
                    requiredString(context.get("sourceDisplayName"), "sourceDisplayName"),
                    requiredString(context.get("sourceLanguage"), "sourceLanguage"),
                    requiredInt(context.get("ordinal"), "ordinal"),
                    requiredString(context.get("displayText"), "displayText"),
                    requiredMap(context.get("location"), "location")
            ));
        }
        return new LookupResultPayloadDto(schemaVersion, entry, contexts);
    }

    private static DictionaryProvenanceDto provenance(Map<String, Object> value) {
        Map<String, Object> rawMetadata = requiredMap(value.get("metadata"), "metadata");
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        rawMetadata.forEach((key, item) -> metadata.put(key, requiredString(item, "metadata value")));
        return new DictionaryProvenanceDto(
                requiredString(value.get("providerId"), "providerId"),
                requiredString(value.get("providerVersion"), "providerVersion"),
                requiredString(value.get("sourceReference"), "sourceReference"),
                optionalString(value.get("sourceUrl")),
                requiredString(value.get("licenseIdentifier"), "licenseIdentifier"),
                requiredString(value.get("attributionText"), "attributionText"),
                metadata
        );
    }

    private static List<DictionarySenseDto> senses(Object value) {
        if (!(value instanceof List<?> rawSenses)) {
            throw new IllegalArgumentException("senses must be a list");
        }
        List<DictionarySenseDto> senses = new ArrayList<>(rawSenses.size());
        for (Object rawSense : rawSenses) {
            Map<String, Object> sense = requiredMap(rawSense, "sense");
            senses.add(new DictionarySenseDto(
                    requiredString(sense.get("partOfSpeech"), "partOfSpeech"),
                    requiredString(sense.get("definition"), "definition"),
                    requiredString(sense.get("providerReference"), "providerReference")
            ));
        }
        return senses;
    }

    private static List<String> stringList(Object value, String field) {
        if (!(value instanceof List<?> rawValues)) {
            throw new IllegalArgumentException(field + " must be a list");
        }
        return rawValues.stream().map(item -> requiredString(item, field + " item")).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requiredMap(Object value, String field) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, item) -> {
            if (!(key instanceof String stringKey)) {
                throw new IllegalArgumentException(field + " keys must be strings");
            }
            result.put(stringKey, item);
        });
        return result;
    }

    private static int requiredInt(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return number.intValue();
    }

    private static String requiredString(Object value, String field) {
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return string;
    }

    private static String optionalString(Object value) {
        return value instanceof String string ? string : "";
    }
}
