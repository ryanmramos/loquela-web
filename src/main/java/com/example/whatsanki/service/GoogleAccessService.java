package com.example.whatsanki.service;

import com.example.whatsanki.domain.AccessEntryEntity;

import java.util.Optional;

/** Applies the Google email allowlist at OAuth sign-in time. */
public interface GoogleAccessService {
    AccessEntryEntity requireActiveAccess(String verifiedEmail);

    Optional<AccessEntryEntity> findActiveAccess(String email);

    String normalizeEmail(String email);
}
