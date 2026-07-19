package com.example.whatsanki.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reproducibility and licensing metadata that follows a definition into lookup and
 * card snapshots. Provider-specific identifiers such as a Wiktionary revision ID live
 * in the immutable metadata map.
 */
public record DictionaryProvenanceDto(
        String providerId,
        String providerVersion,
        String sourceReference,
        String sourceUrl,
        String licenseIdentifier,
        String attributionText,
        Map<String, String> metadata
) {
    public DictionaryProvenanceDto {
        providerId = required(providerId, "providerId");
        providerVersion = required(providerVersion, "providerVersion");
        sourceReference = required(sourceReference, "sourceReference");
        sourceUrl = sourceUrl == null ? "" : sourceUrl.strip();
        licenseIdentifier = required(licenseIdentifier, "licenseIdentifier");
        attributionText = required(attributionText, "attributionText");

        LinkedHashMap<String, String> safeMetadata = new LinkedHashMap<>();
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must not be null");
        }
        metadata.forEach((key, value) -> safeMetadata.put(
                required(key, "metadata key"),
                required(value, "metadata value")
        ));
        metadata = Collections.unmodifiableMap(safeMetadata);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
