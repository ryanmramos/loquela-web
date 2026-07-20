package com.example.whatsanki.service.dictionary;

import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionaryLookupRequestDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguredDictionaryProviderSelectorTest {

    @Test
    void selectsTheConfiguredProviderByCanonicalId() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.getDictionary().setProvider(" FAKE ");
        DictionaryProvider fake = new FakeDictionaryProvider();
        DictionaryProvider alternate = provider("alternate", "v2");

        ConfiguredDictionaryProviderSelector selector = new ConfiguredDictionaryProviderSelector(
                List.of(alternate, fake),
                configuration
        );

        assertThat(selector.activeProvider()).isSameAs(fake);
    }

    @Test
    void rejectsDuplicateProviderIdsAtStartup() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();

        assertThatThrownBy(() -> new ConfiguredDictionaryProviderSelector(
                List.of(provider("fake", "v1"), provider("FAKE", "v2")),
                configuration
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple dictionary providers");
    }

    @Test
    void rejectsAnUnregisteredConfiguredProviderAtStartup() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.getDictionary().setProvider("wiktionary");

        assertThatThrownBy(() -> new ConfiguredDictionaryProviderSelector(
                List.of(new FakeDictionaryProvider()),
                configuration
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wiktionary")
                .hasMessageContaining("fake");
    }

    @Test
    void rejectsAnActiveProviderWithoutVersionedCapabilities() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.getDictionary().setProvider("empty");

        assertThatThrownBy(() -> new ConfiguredDictionaryProviderSelector(
                List.of(provider("empty", " ")),
                configuration
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("version");
    }

    @Test
    void buildsAPairSpecificRouteWithSpecialistAndGenericFallback() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.getDictionary().setProviderOrder(List.of("wiktionary"));
        configuration.getDictionary().setRoutes(Map.of("es:en", List.of("rae", "wiktionary")));
        DictionaryProvider wiktionary = provider("wiktionary", "v1");
        DictionaryProvider rae = provider("rae", "v2");

        ConfiguredDictionaryProviderSelector selector = new ConfiguredDictionaryProviderSelector(
                List.of(wiktionary, rae), configuration
        );

        assertThat(selector.activeProvider()).isInstanceOf(RoutedDictionaryProvider.class);
        assertThat(selector.activeProvider().supportedPairs()).contains(new LanguagePairDto("es", "en"));
    }

    private DictionaryProvider provider(String providerId, String providerVersion) {
        return new DictionaryProvider() {
            @Override
            public String providerId() {
                return providerId;
            }

            @Override
            public String providerVersion() {
                return providerVersion;
            }

            @Override
            public List<LanguagePairDto> supportedPairs() {
                return List.of(new LanguagePairDto("es", "en"));
            }

            @Override
            public Optional<DictionaryEntryDto> lookup(DictionaryLookupRequestDto request) {
                return Optional.empty();
            }
        };
    }
}
