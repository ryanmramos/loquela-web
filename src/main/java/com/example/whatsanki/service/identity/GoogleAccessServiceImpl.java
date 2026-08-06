package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.AccessEntryEntity;
import com.example.whatsanki.repository.AccessEntryRepository;
import com.example.whatsanki.service.GoogleAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GoogleAccessServiceImpl implements GoogleAccessService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final AccessEntryRepository accessEntryRepository;

    @Override
    @Transactional(readOnly = true)
    public AccessEntryEntity requireActiveAccess(String verifiedEmail) {
        String normalized;
        try {
            normalized = normalizeEmail(verifiedEmail);
        } catch (IllegalArgumentException exception) {
            throw accessDenied();
        }
        return accessEntryRepository.findByNormalizedEmail(normalized)
                .filter(entry -> entry.getStatus() == AccessEntryEntity.Status.ACTIVE)
                .orElseThrow(this::accessDenied);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccessEntryEntity> findActiveAccess(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String normalized;
        try {
            normalized = normalizeEmail(email);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        return accessEntryRepository.findByNormalizedEmail(normalized)
                .filter(entry -> entry.getStatus() == AccessEntryEntity.Status.ACTIVE);
    }

    @Override
    public String normalizeEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email is required");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 320 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Email is invalid");
        }
        return normalized;
    }

    private OAuth2AuthenticationException accessDenied() {
        return new OAuth2AuthenticationException(
                new OAuth2Error("access_denied"),
                "This Google account does not have access"
        );
    }
}
