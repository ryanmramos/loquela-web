package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.WhatsAppAddress;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityProtectionServiceImplTest {

    @Test
    void hashesDeterministicallyAndEncryptsReversibly() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.setDataEncryptionKey("test-key");
        IdentityProtectionServiceImpl service = new IdentityProtectionServiceImpl(configuration);
        WhatsAppAddress address = new WhatsAppAddress("whatsapp:+15550000001");

        assertThat(service.hash(address)).isEqualTo(service.hash(address));
        byte[] firstCiphertext = service.encrypt(address);
        byte[] secondCiphertext = service.encrypt(address);
        assertThat(firstCiphertext).isNotEqualTo(secondCiphertext);
        assertThat(service.decrypt(firstCiphertext)).isEqualTo(address);
    }

    @Test
    void refusesToOperateWithoutEncryptionKey() {
        IdentityProtectionServiceImpl service = new IdentityProtectionServiceImpl(new WhatsAnkiConfigurationDto());

        assertThatThrownBy(() -> service.hash(new WhatsAppAddress("whatsapp:+15550000001")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATSANKI_DATA_ENCRYPTION_KEY");
    }
}
