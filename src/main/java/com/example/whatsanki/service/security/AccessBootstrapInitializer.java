package com.example.whatsanki.service.security;

import com.example.whatsanki.domain.AccessEntryAuditEventEntity;
import com.example.whatsanki.domain.AccessEntryEntity;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.AccessEntryAuditEventRepository;
import com.example.whatsanki.repository.AccessEntryRepository;
import com.example.whatsanki.service.GoogleAccessService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** Seeds the first production administrators under a PostgreSQL transaction lock. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AccessBootstrapInitializer implements ApplicationRunner {
    private static final long ADVISORY_LOCK_KEY = 4_724_219_041L;

    private final AccessEntryRepository accessEntryRepository;
    private final AccessEntryAuditEventRepository auditRepository;
    private final GoogleAccessService googleAccessService;
    private final WhatsAnkiConfigurationDto configuration;
    private final Environment environment;
    private final EntityManager entityManager;

    /** Runs once at startup and stops production when no administrator can be established. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(:key)")
                .setParameter("key", ADVISORY_LOCK_KEY)
                .getSingleResult();
        if (accessEntryRepository.existsByRoleAndStatus(
                AccessEntryEntity.Role.ADMIN,
                AccessEntryEntity.Status.ACTIVE
        )) {
            return;
        }

        Set<String> bootstrapEmails = new LinkedHashSet<>();
        for (String email : configuration.getSecurity().getBootstrapAdminEmails()) {
            bootstrapEmails.add(googleAccessService.normalizeEmail(email));
        }
        if (bootstrapEmails.isEmpty()) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException(
                        "Production requires an active admin or WHATSANKI_BOOTSTRAP_ADMIN_EMAILS"
                );
            }
            return;
        }

        Instant now = Instant.now();
        for (String email : bootstrapEmails) {
            AccessEntryEntity entry = accessEntryRepository.findByNormalizedEmail(email)
                    .orElseGet(AccessEntryEntity::new);
            if (entry.getId() != null && entry.getStatus() == AccessEntryEntity.Status.ACTIVE
                    && entry.getRole() == AccessEntryEntity.Role.ADMIN) {
                continue;
            }
            entry.setNormalizedEmail(email);
            entry.setRole(AccessEntryEntity.Role.ADMIN);
            entry.setStatus(AccessEntryEntity.Status.ACTIVE);
            entry.setUpdatedByUserId(null);
            AccessEntryEntity saved = accessEntryRepository.saveAndFlush(entry);
            auditRepository.save(bootstrapEvent(saved, now));
        }
    }

    private AccessEntryAuditEventEntity bootstrapEvent(AccessEntryEntity entry, Instant now) {
        AccessEntryAuditEventEntity event = new AccessEntryAuditEventEntity();
        event.setAccessEntryId(entry.getId());
        event.setAction(AccessEntryAuditEventEntity.Action.BOOTSTRAPPED);
        event.setNewRole(entry.getRole());
        event.setNewStatus(entry.getStatus());
        event.setCreatedAt(now);
        return event;
    }
}
