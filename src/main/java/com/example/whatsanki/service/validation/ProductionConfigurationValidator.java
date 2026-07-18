package com.example.whatsanki.service.validation;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionConfigurationValidator implements ApplicationRunner {

    private final WhatsAnkiConfigurationDto configuration;

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    void validate() {
        List<String> errors = new ArrayList<>();
        requireHttps("WHATSANKI_PUBLIC_BASE_URL", configuration.getPublicBaseUrl(), errors);
        requireText("WHATSANKI_DATA_ENCRYPTION_KEY", configuration.getDataEncryptionKey(), errors);
        requireText("TWILIO_ACCOUNT_SID", configuration.getTwilio().getAccountSid(), errors);
        requireText("TWILIO_AUTH_TOKEN", configuration.getTwilio().getAuthToken(), errors);
        requireText("TWILIO_WHATSAPP_FROM", configuration.getTwilio().getWhatsappFrom(), errors);
        requireHttps("TWILIO_STATUS_CALLBACK_URL", configuration.getTwilio().getStatusCallbackUrl(), errors);
        requireHttps("OBJECT_STORE_ENDPOINT", configuration.getObjectStore().getEndpoint(), errors);

        boolean hasAccessKey = hasText(configuration.getObjectStore().getAccessKey());
        boolean hasSecretKey = hasText(configuration.getObjectStore().getSecretKey());
        if (hasAccessKey != hasSecretKey) {
            // Both blank is valid for production workload identity/IAM roles.
            errors.add("OBJECT_STORE_ACCESS_KEY and OBJECT_STORE_SECRET_KEY must be configured together");
        }
        if ("fake".equalsIgnoreCase(configuration.getDictionary().getProvider())) {
            errors.add("DICTIONARY_PROVIDER must not be fake in production");
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid production configuration: " + String.join("; ", errors));
        }
    }

    private void requireText(String name, String value, List<String> errors) {
        if (!hasText(value)) {
            errors.add(name + " is required");
        }
    }

    private void requireHttps(String name, URI value, List<String> errors) {
        if (value == null || !"https".equalsIgnoreCase(value.getScheme())) {
            errors.add(name + " must be an HTTPS URL");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
