package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.WhatsAppAddress;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.IdentityProtectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Uses HMAC lookups and AES-GCM envelopes for provider identity values. */
@Service
@RequiredArgsConstructor
public class IdentityProtectionServiceImpl implements IdentityProtectionService {
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private final WhatsAnkiConfigurationDto configuration;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Returns a deterministic keyed digest suitable for a database lookup. */
    @Override
    public byte[] hashSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Identity subject must not be blank");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes(), "HmacSHA256"));
            return mac.doFinal(subject.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to hash identity", exception);
        }
    }

    /** Encrypts a recoverable identity with a random IV and authenticated GCM tag. */
    @Override
    public byte[] encryptSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Identity subject must not be blank");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes(), "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(subject.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt identity", exception);
        }
    }

    @Override
    public String decryptSubject(byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length <= IV_BYTES) {
            throw new IllegalArgumentException("Invalid identity ciphertext");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(ciphertext);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes(), "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to decrypt identity", exception);
        }
    }

    @Override
    public byte[] hash(WhatsAppAddress address) {
        return hashSubject(address.value());
    }

    @Override
    public byte[] encrypt(WhatsAppAddress address) {
        return encryptSubject(address.value());
    }

    @Override
    public WhatsAppAddress decrypt(byte[] ciphertext) {
        return new WhatsAppAddress(decryptSubject(ciphertext));
    }

    private byte[] keyBytes() {
        String configuredKey = configuration.getDataEncryptionKey();
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalStateException("WHATSANKI_DATA_ENCRYPTION_KEY is required for identity protection");
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(configuredKey.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive identity encryption key", exception);
        }
    }
}
