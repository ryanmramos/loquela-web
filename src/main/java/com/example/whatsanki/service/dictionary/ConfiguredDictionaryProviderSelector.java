package com.example.whatsanki.service.dictionary;

import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

@Service
@Slf4j
public class ConfiguredDictionaryProviderSelector implements DictionaryProviderSelector {

    private final DictionaryProvider activeProvider;

    public ConfiguredDictionaryProviderSelector(
            List<DictionaryProvider> registeredProviders,
            WhatsAnkiConfigurationDto configuration
    ) {
        // Build the registry once at startup. LOOKUP jobs can then resolve a provider
        // without conditionals, bean-name knowledge, or repeated configuration parsing.
        Map<String, DictionaryProvider> providersById = new LinkedHashMap<>();
        for (DictionaryProvider provider : registeredProviders) {
            String providerId = canonicalProviderId(provider.providerId());
            DictionaryProvider duplicate = providersById.putIfAbsent(providerId, provider);
            if (duplicate != null) {
                throw new IllegalStateException("Multiple dictionary providers registered for " + providerId);
            }
        }

        List<String> configuredOrder = configuredOrder(configuration.getDictionary(), providersById.keySet());
        List<DictionaryProvider> orderedProviders = configuredOrder.stream()
                .map(providersById::get)
                .toList();
        orderedProviders.forEach(this::validateProvider);

        Map<String, List<DictionaryProvider>> routes = configuredRoutes(
                configuration.getDictionary(), providersById
        );
        activeProvider = routes.isEmpty() && orderedProviders.size() == 1
                ? orderedProviders.get(0)
                : new RoutedDictionaryProvider(orderedProviders, routes);

        log.info(
                "Selected dictionary provider providerId={} providerVersion={} configuredProviderCount={} routeCount={} supportedPairCount={}",
                activeProvider.providerId(),
                activeProvider.providerVersion(),
                orderedProviders.size(),
                routes.size(),
                activeProvider.supportedPairs().size()
        );
    }

    @Override
    public DictionaryProvider activeProvider() {
        return activeProvider;
    }

    private String canonicalProviderId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Dictionary provider ID must not be blank");
        }
        String canonical = value.strip().toLowerCase(Locale.ROOT);
        if (!canonical.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalStateException("Invalid dictionary provider ID: " + value);
        }
        return canonical;
    }

    private List<String> configuredOrder(
            WhatsAnkiConfigurationDto.Dictionary dictionary,
            java.util.Set<String> registeredProviderIds
    ) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        List<String> configured = dictionary.getProviderOrder();
        if (configured == null || configured.isEmpty()) {
            ids.add(canonicalProviderId(dictionary.getProvider()));
        } else {
            configured.forEach(value -> ids.add(canonicalProviderId(value)));
        }
        for (String id : ids) {
            if (!registeredProviderIds.contains(id)) {
                throw new IllegalStateException(
                        "Dictionary provider '" + id + "' is not registered; available providers: "
                                + new TreeSet<>(registeredProviderIds)
                );
            }
        }
        return List.copyOf(ids);
    }

    private Map<String, List<DictionaryProvider>> configuredRoutes(
            WhatsAnkiConfigurationDto.Dictionary dictionary,
            Map<String, DictionaryProvider> providersById
    ) {
        Map<String, List<DictionaryProvider>> resolved = new LinkedHashMap<>();
        if (dictionary.getRoutes() == null) {
            return resolved;
        }
        dictionary.getRoutes().forEach((pair, ids) -> {
            String canonicalPair = canonicalPair(pair);
            if (ids == null || ids.isEmpty()) {
                throw new IllegalStateException("Dictionary route must name at least one provider: " + canonicalPair);
            }
            LinkedHashSet<String> routeIds = new LinkedHashSet<>();
            ids.forEach(id -> routeIds.add(canonicalProviderId(id)));
            List<DictionaryProvider> routeProviders = routeIds.stream().map(id -> {
                DictionaryProvider provider = providersById.get(id);
                if (provider == null) {
                    throw new IllegalStateException("Dictionary route " + canonicalPair
                            + " references an unregistered provider: " + id);
                }
                validateProvider(provider);
                return provider;
            }).toList();
            LanguagePairDto routePair = languagePair(canonicalPair);
            if (routeProviders.stream().noneMatch(provider -> provider.supports(routePair))) {
                throw new IllegalStateException("Dictionary route " + canonicalPair
                        + " has no provider that supports the configured pair");
            }
            resolved.put(canonicalPair, routeProviders);
        });
        return Map.copyOf(resolved);
    }

    private String canonicalPair(String value) {
        String[] tags = value == null ? new String[0] : value.strip().split(":", -1);
        if (tags.length != 2) {
            throw new IllegalStateException("Dictionary route keys must use <source>:<definition>: " + value);
        }
        try {
            var pair = new LanguagePairDto(tags[0], tags[1]);
            return pair.sourceLanguage() + ":" + pair.definitionLanguage();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid dictionary route language pair: " + value, exception);
        }
    }

    private LanguagePairDto languagePair(String canonicalPair) {
        String[] tags = canonicalPair.split(":", -1);
        return new LanguagePairDto(tags[0], tags[1]);
    }

    private void validateProvider(DictionaryProvider provider) {
        String providerId = canonicalProviderId(provider.providerId());
        if (provider.providerVersion() == null || provider.providerVersion().isBlank()) {
            throw new IllegalStateException("Dictionary provider version is required for " + providerId);
        }
        if (provider.supportedPairs() == null || provider.supportedPairs().isEmpty()) {
            throw new IllegalStateException("Dictionary provider must advertise at least one language pair: "
                    + providerId);
        }
    }
}
