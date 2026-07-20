package com.example.whatsanki.service;

import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionaryLookupRequestDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.service.exception.DictionaryProviderException;

import java.util.List;
import java.util.Optional;

/**
 * Provider-neutral vocabulary lookup boundary.
 *
 * <p>Implementations must be thread-safe and must not require user identity or corpus
 * content. A future lookup worker sends only the requested term and its snapshotted
 * language pair through this port.</p>
 */
public interface DictionaryProvider {

    /** Stable configuration identifier, such as {@code fake} or {@code wiktionary}. */
    String providerId();

    /** Stable dataset/API version used in cache keys and persisted provenance. */
    String providerVersion();

    /** Canonical language pairs this concrete provider instance can serve. */
    List<LanguagePairDto> supportedPairs();

    /**
     * Returns an immutable entry, or an empty result when the term is not present.
     * Unsupported pairs are programmer/configuration errors and should be rejected
     * before any outbound provider request is made. External adapters classify
     * timeouts, throttling, temporary upstream failures, and deterministic rejection
     * with {@code DictionaryProviderException}; they must not leak response content.
     */
    Optional<DictionaryEntryDto> lookup(DictionaryLookupRequestDto request);

    default boolean supports(LanguagePairDto languagePair) {
        return languagePair != null && supportedPairs().contains(languagePair);
    }
}
