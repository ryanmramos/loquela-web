package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.WhatsAppAddress;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.service.IdentityProtectionService;
import com.example.whatsanki.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserIdentityServiceImpl implements UserIdentityService {
    private final AppUserRepository appUserRepository;
    private final IdentityProtectionService identityProtectionService;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    @Transactional
    public UserId resolveOrCreate(String rawWhatsAppAddress) {
        // Canonicalization happens before hashing so equivalent sender values always
        // resolve to the same deterministic database identity.
        WhatsAppAddress address = new WhatsAppAddress(rawWhatsAppAddress);
        byte[] addressHash = identityProtectionService.hash(address);
        return appUserRepository.findByWhatsappAddressHash(addressHash)
                .map(user -> resolved(user.getId(), false))
                .orElseGet(() -> create(address, addressHash));
    }

    private UserId create(WhatsAppAddress address, byte[] addressHash) {
        UUID candidateId = UUID.randomUUID();
        Instant now = Instant.now();
        LanguagePairDto defaultPair = new LanguagePairDto(
                configuration.getLanguages().getDefaultSource(),
                configuration.getLanguages().getDefaultDefinition()
        );

        // The upsert makes simultaneous first messages from one sender safe. Only one
        // candidate wins; every competing transaction then resolves the persisted row.
        // Canonicalize configured defaults before persistence just as LANGUAGE does.
        int inserted = appUserRepository.insertIfAbsent(
                candidateId,
                addressHash,
                identityProtectionService.encrypt(address),
                defaultPair.sourceLanguage(),
                defaultPair.definitionLanguage(),
                now
        );
        if (inserted == 1) {
            return resolved(candidateId, true);
        }

        UUID existingId = appUserRepository.findByWhatsappAddressHash(addressHash)
                .map(AppUserEntity::getId)
                .orElseThrow(() -> new IllegalStateException("User identity conflict could not be resolved"));
        return resolved(existingId, false);
    }

    private UserId resolved(UUID id, boolean created) {
        log.info("WhatsApp user identity resolved userId={} created={}", id, created);
        return new UserId(id);
    }
}
