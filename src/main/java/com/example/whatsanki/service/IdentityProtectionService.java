package com.example.whatsanki.service;

import com.example.whatsanki.domain.WhatsAppAddress;

/** Hashes provider subjects for lookup and encrypts values that must be recovered later. */
public interface IdentityProtectionService {
    byte[] hashSubject(String subject);
    byte[] encryptSubject(String subject);
    String decryptSubject(byte[] ciphertext);

    byte[] hash(WhatsAppAddress address);
    byte[] encrypt(WhatsAppAddress address);
    WhatsAppAddress decrypt(byte[] ciphertext);
}
