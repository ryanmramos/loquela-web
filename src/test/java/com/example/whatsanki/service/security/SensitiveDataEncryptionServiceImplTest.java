package com.example.whatsanki.service.security;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitiveDataEncryptionServiceImplTest {

    @Test
    void encryptsRandomlyAndRequiresTheSamePurposeToDecrypt() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.setDataEncryptionKey("test-content-key");
        SensitiveDataEncryptionServiceImpl service = new SensitiveDataEncryptionServiceImpl(configuration);

        byte[] first = service.encrypt("El árbol está aquí.", "inbound-message:one");
        byte[] second = service.encrypt("El árbol está aquí.", "inbound-message:one");

        assertThat(first).isNotEqualTo(second);
        assertThat(service.decrypt(first, "inbound-message:one")).isEqualTo("El árbol está aquí.");
        assertThatThrownBy(() -> service.decrypt(first, "inbound-message:two"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to decrypt sensitive content");
    }

    @Test
    void requiresConfiguredEncryptionKey() {
        SensitiveDataEncryptionServiceImpl service =
                new SensitiveDataEncryptionServiceImpl(new WhatsAnkiConfigurationDto());

        assertThatThrownBy(() -> service.encrypt("private", "test-purpose"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATSANKI_DATA_ENCRYPTION_KEY");
    }
}
