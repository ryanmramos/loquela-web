package com.example.whatsanki.service.impl;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationValidatorTest {

    @Test
    void acceptsCompleteProductionConfiguration() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(validConfiguration());

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingSecretsAndNonHttpsEndpoints() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(configuration);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATSANKI_PUBLIC_BASE_URL must be an HTTPS URL")
                .hasMessageContaining("WHATSANKI_DATA_ENCRYPTION_KEY is required")
                .hasMessageContaining("TWILIO_AUTH_TOKEN is required")
                .hasMessageContaining("OBJECT_STORE_ENDPOINT must be an HTTPS URL")
                .hasMessageContaining("DICTIONARY_PROVIDER must not be fake");
    }

    @Test
    void rejectsPartialObjectStoreCredentials() {
        WhatsAnkiConfigurationDto configuration = validConfiguration();
        configuration.getObjectStore().setAccessKey("access-key");

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(configuration);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OBJECT_STORE_ACCESS_KEY and OBJECT_STORE_SECRET_KEY");
    }

    private WhatsAnkiConfigurationDto validConfiguration() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.setPublicBaseUrl(URI.create("https://whatsanki.example"));
        configuration.setDataEncryptionKey("test-encryption-key");
        configuration.getTwilio().setAccountSid("AC00000000000000000000000000000000");
        configuration.getTwilio().setAuthToken("test-auth-token");
        configuration.getTwilio().setWhatsappFrom("whatsapp:+15550000000");
        configuration.getTwilio().setStatusCallbackUrl(
                URI.create("https://whatsanki.example/webhooks/twilio/message-status")
        );
        configuration.getObjectStore().setEndpoint(URI.create("https://objects.example"));
        configuration.getDictionary().setProvider("wiktionary");
        return configuration;
    }
}
