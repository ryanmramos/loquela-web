package com.example.whatsanki.service.impl;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.DictionaryProvider;
import com.example.whatsanki.service.DictionaryProviderSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
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

        String configuredProviderId = canonicalProviderId(configuration.getDictionary().getProvider());
        activeProvider = providersById.get(configuredProviderId);
        if (activeProvider == null) {
            throw new IllegalStateException(
                    "Dictionary provider '" + configuredProviderId + "' is not registered; available providers: "
                            + new TreeSet<>(providersById.keySet())
            );
        }
        if (activeProvider.providerVersion() == null || activeProvider.providerVersion().isBlank()) {
            throw new IllegalStateException("Dictionary provider version is required for " + configuredProviderId);
        }
        if (activeProvider.supportedPairs() == null || activeProvider.supportedPairs().isEmpty()) {
            throw new IllegalStateException("Dictionary provider must advertise at least one language pair: "
                    + configuredProviderId);
        }

        log.info(
                "Selected dictionary provider providerId={} providerVersion={} supportedPairCount={}",
                configuredProviderId,
                activeProvider.providerVersion(),
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
}
