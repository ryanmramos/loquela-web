package com.example.whatsanki.service.dictionary;

import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionaryLookupRequestDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.service.dictionary.DictionaryProvider;
import com.example.whatsanki.service.exception.UnsupportedDictionaryLanguagePairException;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Configuration-owned provider policy that lets a specialist dictionary precede a
 * generic fallback. It does not alter provider results: the contributing adapter's
 * provenance remains on the returned entry.
 */
@Slf4j
final class RoutedDictionaryProvider implements DictionaryProvider {

    static final String PROVIDER_ID = "configured-routing";
    static final String PROVIDER_VERSION = "routing-v1";

    private final List<DictionaryProvider> defaultProviders;
    private final Map<String, List<DictionaryProvider>> providersByPair;
    private final List<LanguagePairDto> supportedPairs;

    RoutedDictionaryProvider(
            List<DictionaryProvider> defaultProviders,
            Map<String, List<DictionaryProvider>> providersByPair
    ) {
        this.defaultProviders = List.copyOf(defaultProviders);
        this.providersByPair = Map.copyOf(providersByPair);
        LinkedHashSet<LanguagePairDto> pairs = new LinkedHashSet<>();
        this.defaultProviders.forEach(provider -> pairs.addAll(provider.supportedPairs()));
        this.providersByPair.values().forEach(providers -> providers.forEach(
                provider -> pairs.addAll(provider.supportedPairs())
        ));
        this.supportedPairs = List.copyOf(pairs);
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public String providerVersion() {
        return PROVIDER_VERSION;
    }

    @Override
    public List<LanguagePairDto> supportedPairs() {
        return supportedPairs;
    }

    @Override
    public Optional<DictionaryEntryDto> lookup(DictionaryLookupRequestDto request) {
        Objects.requireNonNull(request, "request must not be null");
        List<DictionaryProvider> candidates = providersByPair.getOrDefault(
                pairKey(request.languagePair()), defaultProviders
        );
        for (DictionaryProvider provider : candidates) {
            if (!provider.supports(request.languagePair())) {
                continue;
            }
            // An empty result is the only safe fallback condition. A throttled,
            // malformed, unauthorized, or unavailable specialist must retain its own
            // failure classification instead of silently changing lexical authority.
            Optional<DictionaryEntryDto> result = provider.lookup(request);
            if (result.isPresent()) {
                log.info(
                        "Dictionary route resolved result sourceLanguage={} definitionLanguage={} providerId={} termCharacters={}",
                        request.languagePair().sourceLanguage(),
                        request.languagePair().definitionLanguage(),
                        provider.providerId(),
                        request.requestedTerm().codePointCount(0, request.requestedTerm().length())
                );
                return result;
            }
        }
        if (!supports(request.languagePair())) {
            throw new UnsupportedDictionaryLanguagePairException(PROVIDER_ID, request.languagePair());
        }
        log.info(
                "Dictionary route found no definition sourceLanguage={} definitionLanguage={} termCharacters={}",
                request.languagePair().sourceLanguage(),
                request.languagePair().definitionLanguage(),
                request.requestedTerm().codePointCount(0, request.requestedTerm().length())
        );
        return Optional.empty();
    }

    private String pairKey(LanguagePairDto pair) {
        return pair.sourceLanguage() + ":" + pair.definitionLanguage();
    }
}
