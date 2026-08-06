package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.AccessEntryAuditEventEntity;
import com.example.whatsanki.domain.AccessEntryEntity;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.AdminAccessEntryDto;
import com.example.whatsanki.dto.AdminAccessEntryListDto;
import com.example.whatsanki.dto.AdminAccessRole;
import com.example.whatsanki.dto.AdminAccessStatus;
import com.example.whatsanki.dto.CreateAdminAccessEntryRequestDto;
import com.example.whatsanki.dto.UpdateAdminAccessEntryRequestDto;
import com.example.whatsanki.repository.AccessEntryAuditEventRepository;
import com.example.whatsanki.repository.AccessEntryRepository;
import com.example.whatsanki.repository.UserIdentityRepository;
import com.example.whatsanki.service.AdminAccessService;
import com.example.whatsanki.service.GoogleAccessService;
import com.example.whatsanki.service.IdentityProtectionService;
import com.example.whatsanki.service.exception.AdminAccessException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAccessServiceImpl implements AdminAccessService {
    private final AccessEntryRepository accessEntryRepository;
    private final AccessEntryAuditEventRepository auditRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final IdentityProtectionService identityProtectionService;
    private final GoogleAccessService googleAccessService;

    @Override
    @Transactional(readOnly = true)
    public AdminAccessEntryListDto list(
            String googleSubject,
            String search,
            AdminAccessRole role,
            AdminAccessStatus status,
            int page,
            int pageSize
    ) {
        requireActor(googleSubject);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT);
        Specification<AccessEntryEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!normalizedSearch.isEmpty()) {
                predicates.add(builder.like(root.get("normalizedEmail"), "%" + normalizedSearch + "%"));
            }
            if (role != null) {
                predicates.add(builder.equal(root.get("role"), toEntityRole(role)));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), toEntityStatus(status)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<AccessEntryEntity> result = accessEntryRepository.findAll(
                specification,
                PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "updatedAt"))
        );
        return new AdminAccessEntryListDto(
                result.getContent().stream().map(this::toDto).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Override
    @Transactional
    public AdminAccessEntryDto create(
            String googleSubject,
            CreateAdminAccessEntryRequestDto request,
            boolean recentlyAuthenticated
    ) {
        Actor actor = requireActor(googleSubject);
        String email = normalizeRequestEmail(request.email());
        AccessEntryEntity.Role requestedRole = toEntityRole(request.role());
        AccessEntryEntity existing = accessEntryRepository.findByNormalizedEmail(email).orElse(null);
        boolean sensitive = requestedRole == AccessEntryEntity.Role.ADMIN
                || existing != null && existing.getRole() == AccessEntryEntity.Role.ADMIN;
        requireSensitiveAuthorization(sensitive, recentlyAuthenticated, email, request.confirmationEmail());

        if (existing != null && existing.getStatus() == AccessEntryEntity.Status.ACTIVE) {
            throw new AdminAccessException(HttpStatus.CONFLICT, "ACCESS_ENTRY_EXISTS");
        }

        AccessEntryEntity entry = existing == null ? new AccessEntryEntity() : existing;
        AccessEntryEntity.Role previousRole = entry.getRole();
        AccessEntryEntity.Status previousStatus = entry.getStatus();
        entry.setNormalizedEmail(email);
        entry.setRole(requestedRole);
        entry.setStatus(AccessEntryEntity.Status.ACTIVE);
        if (existing == null) {
            entry.setCreatedByUserId(actor.userId());
        }
        entry.setUpdatedByUserId(actor.userId());
        AccessEntryEntity saved = accessEntryRepository.saveAndFlush(entry);
        if (existing == null) {
            audit(saved, actor.userId(), AccessEntryAuditEventEntity.Action.CREATED,
                    null, saved.getRole(), null, saved.getStatus());
        } else {
            if (previousRole != saved.getRole()) {
                audit(saved, actor.userId(), AccessEntryAuditEventEntity.Action.ROLE_CHANGED,
                        previousRole, saved.getRole(), previousStatus, previousStatus);
            }
            audit(saved, actor.userId(), AccessEntryAuditEventEntity.Action.REACTIVATED,
                    saved.getRole(), saved.getRole(), previousStatus, saved.getStatus());
        }
        return toDto(saved);
    }

    @Override
    @Transactional
    public AdminAccessEntryDto update(
            String googleSubject,
            UUID reference,
            UpdateAdminAccessEntryRequestDto request,
            boolean recentlyAuthenticated
    ) {
        Actor actor = requireActor(googleSubject);
        AccessEntryEntity entry = accessEntryRepository.findById(reference)
                .orElseThrow(() -> new AdminAccessException(HttpStatus.NOT_FOUND, "ACCESS_ENTRY_NOT_FOUND"));
        if (entry.getVersion() != request.version()) {
            throw new AdminAccessException(HttpStatus.CONFLICT, "ACCESS_ENTRY_VERSION_CONFLICT");
        }
        AccessEntryEntity.Role requestedRole = toEntityRole(request.role());
        AccessEntryEntity.Status requestedStatus = toEntityStatus(request.status());
        boolean changed = entry.getRole() != requestedRole || entry.getStatus() != requestedStatus;
        if (!changed) {
            return toDto(entry);
        }

        boolean currentAdmin = isActiveAdmin(entry);
        boolean requestedAdmin = requestedRole == AccessEntryEntity.Role.ADMIN
                && requestedStatus == AccessEntryEntity.Status.ACTIVE;
        requireSensitiveAuthorization(
                entry.getRole() == AccessEntryEntity.Role.ADMIN
                        || requestedRole == AccessEntryEntity.Role.ADMIN,
                recentlyAuthenticated,
                entry.getNormalizedEmail(),
                request.confirmationEmail()
        );
        if (entry.getId().equals(actor.accessEntryId()) && currentAdmin && !requestedAdmin) {
            throw new AdminAccessException(HttpStatus.CONFLICT, "SELF_ADMIN_CHANGE_FORBIDDEN");
        }
        if (currentAdmin && !requestedAdmin) {
            List<AccessEntryEntity> activeAdmins = accessEntryRepository.lockActiveAdmins();
            if (activeAdmins.size() <= 1) {
                throw new AdminAccessException(HttpStatus.CONFLICT, "LAST_ADMIN_REQUIRED");
            }
        }

        AccessEntryEntity.Role previousRole = entry.getRole();
        AccessEntryEntity.Status previousStatus = entry.getStatus();
        entry.setRole(requestedRole);
        entry.setStatus(requestedStatus);
        entry.setUpdatedByUserId(actor.userId());
        AccessEntryEntity saved = accessEntryRepository.saveAndFlush(entry);
        if (previousRole != saved.getRole()) {
            audit(saved, actor.userId(), AccessEntryAuditEventEntity.Action.ROLE_CHANGED,
                    previousRole, saved.getRole(), previousStatus, saved.getStatus());
        }
        if (previousStatus != saved.getStatus()) {
            AccessEntryAuditEventEntity.Action action = saved.getStatus() == AccessEntryEntity.Status.ACTIVE
                    ? AccessEntryAuditEventEntity.Action.REACTIVATED
                    : AccessEntryAuditEventEntity.Action.REVOKED;
            audit(saved, actor.userId(), action,
                    saved.getRole(), saved.getRole(), previousStatus, saved.getStatus());
        }
        return toDto(saved);
    }

    private Actor requireActor(String googleSubject) {
        UserIdentityEntity identity = userIdentityRepository.findByProviderAndProviderSubjectHash(
                UserIdentityEntity.Provider.GOOGLE,
                identityProtectionService.hashSubject(googleSubject)
        ).orElseThrow(() -> new AdminAccessException(HttpStatus.FORBIDDEN, "ADMIN_ACCESS_REQUIRED"));
        AccessEntryEntity access = googleAccessService.findActiveAccess(identity.getDisplayEmail())
                .filter(entry -> entry.getRole() == AccessEntryEntity.Role.ADMIN)
                .orElseThrow(() -> new AdminAccessException(HttpStatus.FORBIDDEN, "ADMIN_ACCESS_REQUIRED"));
        return new Actor(identity.getUserId(), access.getId());
    }

    private String normalizeRequestEmail(String email) {
        try {
            return googleAccessService.normalizeEmail(email);
        } catch (IllegalArgumentException exception) {
            throw new AdminAccessException(HttpStatus.BAD_REQUEST, "INVALID_ACCESS_EMAIL");
        }
    }

    private void requireSensitiveAuthorization(
            boolean sensitive,
            boolean recentlyAuthenticated,
            String targetEmail,
            String confirmationEmail
    ) {
        if (!sensitive) {
            return;
        }
        if (!recentlyAuthenticated) {
            throw new AdminAccessException(HttpStatus.FORBIDDEN, "RECENT_AUTHENTICATION_REQUIRED");
        }
        String normalizedConfirmation;
        try {
            normalizedConfirmation = googleAccessService.normalizeEmail(confirmationEmail);
        } catch (IllegalArgumentException exception) {
            throw new AdminAccessException(HttpStatus.BAD_REQUEST, "ADMIN_CONFIRMATION_REQUIRED");
        }
        if (!targetEmail.equals(normalizedConfirmation)) {
            throw new AdminAccessException(HttpStatus.BAD_REQUEST, "ADMIN_CONFIRMATION_REQUIRED");
        }
    }

    private boolean isActiveAdmin(AccessEntryEntity entry) {
        return entry.getRole() == AccessEntryEntity.Role.ADMIN
                && entry.getStatus() == AccessEntryEntity.Status.ACTIVE;
    }

    private void audit(
            AccessEntryEntity entry,
            UUID actorUserId,
            AccessEntryAuditEventEntity.Action action,
            AccessEntryEntity.Role previousRole,
            AccessEntryEntity.Role newRole,
            AccessEntryEntity.Status previousStatus,
            AccessEntryEntity.Status newStatus
    ) {
        AccessEntryAuditEventEntity event = new AccessEntryAuditEventEntity();
        event.setAccessEntryId(entry.getId());
        event.setActorUserId(actorUserId);
        event.setAction(action);
        event.setPreviousRole(previousRole);
        event.setNewRole(newRole);
        event.setPreviousStatus(previousStatus);
        event.setNewStatus(newStatus);
        event.setCorrelationId(MDC.get("correlationId"));
        auditRepository.save(event);
    }

    private AdminAccessEntryDto toDto(AccessEntryEntity entry) {
        return new AdminAccessEntryDto(
                entry.getId(),
                entry.getNormalizedEmail(),
                AdminAccessRole.valueOf(entry.getRole().name()),
                AdminAccessStatus.valueOf(entry.getStatus().name()),
                entry.getVersion(),
                entry.getCreatedByUserId(),
                entry.getUpdatedByUserId(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }

    private record Actor(UUID userId, UUID accessEntryId) {
    }

    private AccessEntryEntity.Role toEntityRole(AdminAccessRole role) {
        return role == null ? null : AccessEntryEntity.Role.valueOf(role.name());
    }

    private AccessEntryEntity.Status toEntityStatus(AdminAccessStatus status) {
        return status == null ? null : AccessEntryEntity.Status.valueOf(status.name());
    }
}
