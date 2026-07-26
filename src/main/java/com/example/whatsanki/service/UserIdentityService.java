package com.example.whatsanki.service;

import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.domain.WhatsAppAddress;

import java.util.Optional;

public interface UserIdentityService {
    UserId resolveOrCreate(String rawWhatsAppAddress);

    UserId resolveOrCreateGoogle(String subject, String verifiedEmail, String displayName);

    void linkWhatsApp(UserId userId, String rawWhatsAppAddress);

    Optional<UserId> findUserId(UserIdentityEntity.Provider provider, String subject);

    WhatsAppAddress requireWhatsAppAddress(UserId userId);

    boolean hasIdentity(UserId userId, UserIdentityEntity.Provider provider);
}
