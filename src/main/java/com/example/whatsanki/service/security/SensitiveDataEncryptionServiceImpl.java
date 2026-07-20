package com.example.whatsanki.service.security;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.SensitiveDataEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class SensitiveDataEncryptionServiceImpl implements SensitiveDataEncryptionService {

    private static final byte FORMAT_VERSION = 1;
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final byte[] KEY_CONTEXT =
            "WhatsAnki sensitive content AES-GCM v1\0".getBytes(StandardCharsets.UTF_8);

    private final WhatsAnkiConfigurationDto configuration;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public byte[] encrypt(String plaintext, String purpose) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        validatePurpose(purpose);

        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv, purpose);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + iv.length + encrypted.length)
                    .put(FORMAT_VERSION)
                    .put(iv)
                    .put(encrypted)
                    .array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt sensitive content", exception);
        }
    }

    @Override
    public String decrypt(byte[] ciphertext, String purpose) {
        validatePurpose(purpose);
        if (ciphertext == null || ciphertext.length <= 1 + IV_BYTES) {
            throw new IllegalArgumentException("Invalid sensitive-content ciphertext");
        }

        ByteBuffer envelope = ByteBuffer.wrap(ciphertext);
        if (envelope.get() != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported sensitive-content ciphertext format");
        }

        byte[] iv = new byte[IV_BYTES];
        envelope.get(iv);
        byte[] encrypted = new byte[envelope.remaining()];
        envelope.get(encrypted);
        try {
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, iv, purpose);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to decrypt sensitive content", exception);
        }
    }

    private Cipher cipher(int mode, byte[] iv, String purpose) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(keyBytes(), "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        // Binding ciphertext to a record-specific purpose prevents encrypted content
        // copied from one inbound message from being decrypted as another message.
        cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private byte[] keyBytes() {
        String configuredKey = configuration.getDataEncryptionKey();
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalStateException("WHATSANKI_DATA_ENCRYPTION_KEY is required for sensitive content");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(KEY_CONTEXT);
            return digest.digest(configuredKey.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive sensitive-content encryption key", exception);
        }
    }

    private void validatePurpose(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("purpose must not be blank");
        }
    }
}
