package com.example.whatsanki.service.validation;

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
                .hasMessageContaining("WHATSANKI_GOOGLE_OAUTH_ENABLED must be true")
                .hasMessageContaining("WHATSANKI_SECURE_COOKIES must be true")
                .hasMessageContaining("DICTIONARY_PROVIDER must not be fake");
    }

    @Test
    void rejectsPartialObjectStoreCredentials() {
        WhatsAnkiConfigurationDto configuration = validConfiguration();
        configuration.getObjectStore().setSecretKey("");

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(configuration);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OBJECT_STORE_ACCESS_KEY and OBJECT_STORE_SECRET_KEY");
    }

    @Test
    void rejectsLocalLoginInProduction() {
        WhatsAnkiConfigurationDto configuration = validConfiguration();
        configuration.getSecurity().setLocalLoginEnabled(true);

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(configuration);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATSANKI_LOCAL_LOGIN_ENABLED must be false");
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
        configuration.getObjectStore().setAccessKey("access-key");
        configuration.getObjectStore().setSecretKey("secret-key");
        configuration.getDictionary().setProvider("wiktionary");
        configuration.getSecurity().setGoogleOauthEnabled(true);
        configuration.getSecurity().setSecureCookies(true);
        configuration.getSecurity().setFrontendBaseUrl(URI.create("https://whatsanki.example"));
        return configuration;
    }
}
