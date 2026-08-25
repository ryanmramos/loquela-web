package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.domain.WhatsAppAddress;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.UserIdentityRepository;
import com.example.whatsanki.service.IdentityProtectionService;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.card.DefaultDeckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Resolves provider identities with lookup hashes and encrypted recovery values. */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserIdentityServiceImpl implements UserIdentityService {
    private final AppUserRepository appUserRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final IdentityProtectionService identityProtectionService;
    private final DefaultDeckService defaultDeckService;
    private final WhatsAnkiConfigurationDto configuration;

    /** Creates or reuses the user linked to a normalized WhatsApp address. */
    @Override
    @Transactional
    public UserId resolveOrCreate(String rawWhatsAppAddress) {
        WhatsAppAddress address = new WhatsAppAddress(rawWhatsAppAddress);
        byte[] addressHash = identityProtectionService.hash(address);
        return findByHash(UserIdentityEntity.Provider.WHATSAPP, addressHash)
                .orElseGet(() -> create(
                        UserIdentityEntity.Provider.WHATSAPP,
                        addressHash,
                        identityProtectionService.encrypt(address),
                        null,
                        null,
                        false
                ));
    }

    /** Creates or reuses a Google user and provisions the default deck for new accounts. */
    @Override
    @Transactional
    public UserId resolveOrCreateGoogle(String subject, String verifiedEmail, String displayName) {
        String normalizedSubject = requireSubject(subject);
        byte[] subjectHash = identityProtectionService.hashSubject(normalizedSubject);
        return findByHash(UserIdentityEntity.Provider.GOOGLE, subjectHash)
                .orElseGet(() -> create(
                        UserIdentityEntity.Provider.GOOGLE,
                        subjectHash,
                        null,
                        normalizeDisplayValue(verifiedEmail, 320),
                        normalizeDisplayValue(displayName, 200),
                        true
                ));
    }

    @Override
    @Transactional
    public void linkWhatsApp(UserId userId, String rawWhatsAppAddress) {
        AppUserEntity user = appUserRepository.findByIdAndStatus(userId.value(), AppUserEntity.Status.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Active user does not exist"));
        WhatsAppAddress address = new WhatsAppAddress(rawWhatsAppAddress);
        byte[] subjectHash = identityProtectionService.hash(address);

        Optional<UserIdentityEntity> subjectOwner = userIdentityRepository.findByProviderAndProviderSubjectHash(
                UserIdentityEntity.Provider.WHATSAPP,
                subjectHash
        );
        if (subjectOwner.isPresent()) {
            if (subjectOwner.get().getUserId().equals(user.getId())) {
                return;
            }
            throw new IllegalStateException("WhatsApp identity is already linked to another user");
        }
        if (userIdentityRepository.existsByUserIdAndProvider(
                user.getId(),
                UserIdentityEntity.Provider.WHATSAPP
        )) {
            throw new IllegalStateException("User already has a linked WhatsApp identity");
        }

        Instant now = Instant.now();
        int inserted = userIdentityRepository.insertIfAbsent(
                UUID.randomUUID(),
                user.getId(),
                UserIdentityEntity.Provider.WHATSAPP.name(),
                subjectHash,
                identityProtectionService.encrypt(address),
                null,
                null,
                now
        );
        if (inserted != 1) {
            throw new IllegalStateException("WhatsApp identity could not be linked");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserId> findUserId(UserIdentityEntity.Provider provider, String subject) {
        return findByHash(provider, identityProtectionService.hashSubject(requireSubject(subject)));
    }

    @Override
    @Transactional(readOnly = true)
    public WhatsAppAddress requireWhatsAppAddress(UserId userId) {
        UserIdentityEntity identity = userIdentityRepository
                .findByUserIdAndProvider(userId.value(), UserIdentityEntity.Provider.WHATSAPP)
                .orElseThrow(() -> new IllegalStateException("User has no linked WhatsApp identity"));
        return identityProtectionService.decrypt(identity.getProviderSubjectCiphertext());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasIdentity(UserId userId, UserIdentityEntity.Provider provider) {
        return userIdentityRepository.existsByUserIdAndProvider(userId.value(), provider);
    }

    private UserId create(
            UserIdentityEntity.Provider provider,
            byte[] subjectHash,
            byte[] subjectCiphertext,
            String displayEmail,
            String displayName,
            boolean createDefaultDeck
    ) {
        UUID candidateId = UUID.randomUUID();
        Instant now = Instant.now();
        LanguagePairDto defaultPair = new LanguagePairDto(
                configuration.getLanguages().getDefaultSource(),
                configuration.getLanguages().getDefaultDefinition()
        );

        AppUserEntity candidate = new AppUserEntity();
        candidate.setId(candidateId);
        candidate.setSourceLanguage(defaultPair.sourceLanguage());
        candidate.setDefinitionLanguage(defaultPair.definitionLanguage());
        candidate.setStatus(AppUserEntity.Status.ACTIVE);
        candidate.setCreatedAt(now);
        candidate.setUpdatedAt(now);
        appUserRepository.saveAndFlush(candidate);

        int inserted = userIdentityRepository.insertIfAbsent(
                UUID.randomUUID(),
                candidateId,
                provider.name(),
                subjectHash,
                subjectCiphertext,
                displayEmail,
                displayName,
                now
        );
        if (inserted == 1) {
            if (createDefaultDeck) {
                defaultDeckService.resolveOrCreate(new UserId(candidateId), now);
            }
            return resolved(candidateId, true);
        }

        appUserRepository.deleteById(candidateId);
        appUserRepository.flush();
        UUID existingId = userIdentityRepository
                .findByProviderAndProviderSubjectHash(provider, subjectHash)
                .map(UserIdentityEntity::getUserId)
                .orElseThrow(() -> new IllegalStateException("User identity conflict could not be resolved"));
        return resolved(existingId, false);
    }

    private Optional<UserId> findByHash(UserIdentityEntity.Provider provider, byte[] subjectHash) {
        return userIdentityRepository.findByProviderAndProviderSubjectHash(provider, subjectHash)
                .map(UserIdentityEntity::getUserId)
                .map(id -> resolved(id, false));
    }

    private String requireSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Provider subject must not be blank");
        }
        return subject.trim();
    }

    private String normalizeDisplayValue(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private UserId resolved(UUID id, boolean created) {
        log.info("User identity resolved userId={} created={}", id, created);
        return new UserId(id);
    }
}
