package com.example.whatsanki.service.security;

import com.example.whatsanki.domain.AccessEntryEntity;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.repository.AccessEntryRepository;
import com.example.whatsanki.repository.UserIdentityRepository;
import com.example.whatsanki.service.IdentityProtectionService;
import com.example.whatsanki.service.GoogleAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("adminAuthorization")
@RequiredArgsConstructor
@Slf4j
public class AdminAuthorization {
    private final UserIdentityRepository userIdentityRepository;
    private final AccessEntryRepository accessEntryRepository;
    private final IdentityProtectionService identityProtectionService;
    private final GoogleAccessService googleAccessService;

    @Transactional(readOnly = true)
    public boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        try {
            return userIdentityRepository.findByProviderAndProviderSubjectHash(
                            UserIdentityEntity.Provider.GOOGLE,
                            identityProtectionService.hashSubject(authentication.getName())
                    )
                    .map(UserIdentityEntity::getDisplayEmail)
                    .flatMap(email -> accessEntryRepository.findByNormalizedEmail(
                            googleAccessService.normalizeEmail(email)
                    ))
                    .filter(entry -> entry.getStatus() == AccessEntryEntity.Status.ACTIVE)
                    .filter(entry -> entry.getRole() == AccessEntryEntity.Role.ADMIN)
                    .isPresent();
        } catch (RuntimeException exception) {
            log.error("Administrative authorization failed closed", exception);
            return false;
        }
    }
}

