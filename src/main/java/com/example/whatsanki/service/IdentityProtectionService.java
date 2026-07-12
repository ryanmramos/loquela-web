package com.example.whatsanki.service;

import com.example.whatsanki.domain.WhatsAppAddress;

public interface IdentityProtectionService {
    byte[] hash(WhatsAppAddress address);
    byte[] encrypt(WhatsAppAddress address);
    WhatsAppAddress decrypt(byte[] ciphertext);
}
