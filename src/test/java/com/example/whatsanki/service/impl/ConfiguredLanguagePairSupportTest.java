package com.example.whatsanki.service.impl;

import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguredLanguagePairSupportTest {

    @Test
    void canonicalizesDeduplicatesAndChecksConfiguredPairs() {
        WhatsAnkiConfigurationDto configuration = configuration(
                "es", "en", List.of("ES:EN", "fr:en", "es:en")
        );

        ConfiguredLanguagePairSupport support = support(configuration);

        assertThat(support.supportedPairs()).containsExactly(
                new LanguagePairDto("es", "en"),
                new LanguagePairDto("fr", "en")
        );
        assertThat(support.supports(new LanguagePairDto("FR", "EN"))).isTrue();
        assertThat(support.supports(new LanguagePairDto("en", "en"))).isFalse();
    }

    @Test
    void supportsMonolingualPairWhenProviderCapabilityIncludesIt() {
        WhatsAnkiConfigurationDto configuration = configuration(
                "es", "es", List.of("es:es", "es:en")
        );

        ConfiguredLanguagePairSupport support = support(configuration);

        assertThat(support.supports(new LanguagePairDto("ES", "es"))).isTrue();
        assertThat(support.supportedPairs()).contains(new LanguagePairDto("es", "es"));
    }

    @Test
    void rejectsMalformedConfiguredPair() {
        WhatsAnkiConfigurationDto configuration = configuration(
                "es", "en", List.of("es-en")
        );

        assertThatThrownBy(() -> support(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("<source>:<definition>");
    }

    @Test
    void rejectsConfiguredPairWithInvalidLanguageTag() {
        WhatsAnkiConfigurationDto configuration = configuration(
                "es", "en", List.of("invalid_tag:en")
        );

        assertThatThrownBy(() -> support(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid configured language pair");
    }

    @Test
    void rejectsDefaultPairThatProviderDoesNotSupport() {
        WhatsAnkiConfigurationDto configuration = configuration(
                "es", "en", List.of("en:es")
        );

        assertThatThrownBy(() -> support(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default language pair");
    }

    @Test
    void rejectsConfiguredPairsOutsideTheActiveProviderCapabilities() {
        WhatsAnkiConfigurationDto configuration = configuration(
                "de", "en", List.of("de:en")
        );

        assertThatThrownBy(() -> support(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("de:en")
                .hasMessageContaining("fake");
    }

    private ConfiguredLanguagePairSupport support(WhatsAnkiConfigurationDto configuration) {
        FakeDictionaryProvider provider = new FakeDictionaryProvider();
        return new ConfiguredLanguagePairSupport(configuration, () -> provider);
    }

    private WhatsAnkiConfigurationDto configuration(
            String defaultSource,
            String defaultDefinition,
            List<String> supportedPairs
    ) {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.getLanguages().setDefaultSource(defaultSource);
        configuration.getLanguages().setDefaultDefinition(defaultDefinition);
        configuration.getLanguages().setSupportedPairs(supportedPairs);
        return configuration;
    }
}
