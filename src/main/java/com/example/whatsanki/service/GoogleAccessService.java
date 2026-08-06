package com.example.whatsanki.service;

import com.example.whatsanki.domain.AccessEntryEntity;

import java.util.Optional;

public interface GoogleAccessService {
    AccessEntryEntity requireActiveAccess(String verifiedEmail);

    Optional<AccessEntryEntity> findActiveAccess(String email);

    String normalizeEmail(String email);
}

