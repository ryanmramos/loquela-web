package com.example.whatsanki.service;

public interface SensitiveDataEncryptionService {
    byte[] encrypt(String plaintext, String purpose);

    String decrypt(byte[] ciphertext, String purpose);
}
