package com.example.whatsanki.dto;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Immutable provider-neutral result consumed by the future lookup worker. */
public record DictionaryEntryDto(
        String requestedTerm,
        String lemma,
        List<String> forms,
        List<DictionarySenseDto> senses,
        LanguagePairDto languagePair,
        DictionaryProvenanceDto provenance
) {
    public DictionaryEntryDto {
        requestedTerm = required(requestedTerm, "requestedTerm");
        lemma = required(lemma, "lemma");
        forms = immutableTerms(forms);
        if (senses == null || senses.isEmpty() || senses.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("senses must contain at least one non-null sense");
        }
        senses = List.copyOf(senses);
        Objects.requireNonNull(languagePair, "languagePair must not be null");
        Objects.requireNonNull(provenance, "provenance must not be null");
    }

    /**
     * Stable OR-set for the corpus search stage: the query, lemma, then inflected
     * forms. Ordering is meaningful because the future lookup worker can search the
     * strongest terms first while deduplicating provider aliases.
     */
    public List<String> searchTerms() {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        terms.add(requestedTerm);
        terms.add(lemma);
        terms.addAll(forms);
        return List.copyOf(terms);
    }

    private static List<String> immutableTerms(List<String> values) {
        if (values == null) {
            throw new IllegalArgumentException("forms must not be null");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        values.forEach(value -> unique.add(required(value, "form")));
        return List.copyOf(unique);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
