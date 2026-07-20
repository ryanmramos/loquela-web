package com.example.whatsanki.service.identity;

import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.dictionary.DictionaryProvider;
import com.example.whatsanki.service.dictionary.DictionaryProviderSelector;
import com.example.whatsanki.service.LanguagePairSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@Slf4j
public class ConfiguredLanguagePairSupport implements LanguagePairSupport {

    private final List<LanguagePairDto> supportedPairs;

    public ConfiguredLanguagePairSupport(
            WhatsAnkiConfigurationDto configuration,
            DictionaryProviderSelector dictionaryProviderSelector
    ) {
        // Parse configuration once so requests compare typed canonical pairs instead of
        // repeatedly interpreting provider capability strings on the webhook thread.
        LinkedHashSet<LanguagePairDto> canonicalPairs = new LinkedHashSet<>();
        for (String configuredPair : configuration.getLanguages().getSupportedPairs()) {
            canonicalPairs.add(parse(configuredPair));
        }
        supportedPairs = List.copyOf(canonicalPairs);

        DictionaryProvider activeProvider = dictionaryProviderSelector.activeProvider();
        for (LanguagePairDto supportedPair : supportedPairs) {
            if (!activeProvider.supports(supportedPair)) {
                throw new IllegalStateException(
                        "Configured language pair " + supportedPair.sourceLanguage() + ":"
                                + supportedPair.definitionLanguage() + " is not supported by dictionary provider "
                                + activeProvider.providerId()
                );
            }
        }

        LanguagePairDto defaultPair = new LanguagePairDto(
                configuration.getLanguages().getDefaultSource(),
                configuration.getLanguages().getDefaultDefinition()
        );
        if (!supportedPairs.contains(defaultPair)) {
            throw new IllegalStateException(
                    "The configured default language pair must be present in whatsanki.languages.supported-pairs"
            );
        }
        log.info(
                "Configured supported language pairs providerId={} count={}",
                activeProvider.providerId(),
                supportedPairs.size()
        );
    }

    @Override
    public boolean supports(LanguagePairDto languagePair) {
        return languagePair != null && supportedPairs.contains(languagePair);
    }

    @Override
    public List<LanguagePairDto> supportedPairs() {
        return supportedPairs;
    }

    private LanguagePairDto parse(String configuredPair) {
        String[] tags = configuredPair == null ? new String[0] : configuredPair.strip().split(":", -1);
        if (tags.length != 2) {
            throw new IllegalStateException(
                    "Language pairs must use <source>:<definition> format: " + configuredPair
            );
        }
        try {
            return new LanguagePairDto(tags[0], tags[1]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid configured language pair: " + configuredPair, exception);
        }
    }
}
