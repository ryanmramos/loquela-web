package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.AccessEntryAuditEventEntity;
import com.example.whatsanki.domain.AccessEntryEntity;
import com.example.whatsanki.repository.AccessEntryAuditEventRepository;
import com.example.whatsanki.repository.AccessEntryRepository;
import com.example.whatsanki.service.GoogleAccessService;
import com.example.whatsanki.service.LocalAccessProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Local-only provisioning for fixed development identities. */
@Service
@Profile({"local", "test"})
@RequiredArgsConstructor
public class LocalAccessProvisioningServiceImpl implements LocalAccessProvisioningService {
    private final AccessEntryRepository accessEntryRepository;
    private final AccessEntryAuditEventRepository auditRepository;
    private final GoogleAccessService googleAccessService;

    @Override
    @Transactional
    public void ensureAdminAccess(String email) {
        String normalizedEmail = googleAccessService.normalizeEmail(email);
        AccessEntryEntity entry = accessEntryRepository.findByNormalizedEmail(normalizedEmail)
                .orElseGet(AccessEntryEntity::new);
        if (entry.getId() != null
                && entry.getRole() == AccessEntryEntity.Role.ADMIN
                && entry.getStatus() == AccessEntryEntity.Status.ACTIVE) {
            return;
        }

        boolean created = entry.getId() == null;
        AccessEntryEntity.Role previousRole = entry.getRole();
        AccessEntryEntity.Status previousStatus = entry.getStatus();
        entry.setNormalizedEmail(normalizedEmail);
        entry.setRole(AccessEntryEntity.Role.ADMIN);
        entry.setStatus(AccessEntryEntity.Status.ACTIVE);
        AccessEntryEntity saved = accessEntryRepository.saveAndFlush(entry);

        AccessEntryAuditEventEntity event = new AccessEntryAuditEventEntity();
        event.setAccessEntryId(saved.getId());
        event.setAction(created
                ? AccessEntryAuditEventEntity.Action.CREATED
                : previousStatus == AccessEntryEntity.Status.REVOKED
                        ? AccessEntryAuditEventEntity.Action.REACTIVATED
                        : AccessEntryAuditEventEntity.Action.ROLE_CHANGED);
        event.setPreviousRole(previousRole);
        event.setNewRole(saved.getRole());
        event.setPreviousStatus(previousStatus);
        event.setNewStatus(saved.getStatus());
        auditRepository.save(event);
    }
}
