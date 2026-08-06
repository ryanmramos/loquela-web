package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.AccessEntryAuditEventEntity;
import com.example.whatsanki.domain.AccessEntryEntity;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.AdminAccessRole;
import com.example.whatsanki.dto.AdminAccessStatus;
import com.example.whatsanki.dto.CreateAdminAccessEntryRequestDto;
import com.example.whatsanki.dto.UpdateAdminAccessEntryRequestDto;
import com.example.whatsanki.repository.AccessEntryAuditEventRepository;
import com.example.whatsanki.repository.AccessEntryRepository;
import com.example.whatsanki.repository.UserIdentityRepository;
import com.example.whatsanki.service.GoogleAccessService;
import com.example.whatsanki.service.IdentityProtectionService;
import com.example.whatsanki.service.exception.AdminAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccessServiceImplTest {
    private static final String SUBJECT = "google-subject";
    private static final String ACTOR_EMAIL = "admin@example.com";

    @Mock
    private AccessEntryRepository accessEntryRepository;
    @Mock
    private AccessEntryAuditEventRepository auditRepository;
    @Mock
    private UserIdentityRepository userIdentityRepository;
    @Mock
    private IdentityProtectionService identityProtectionService;
    @Mock
    private GoogleAccessService googleAccessService;

    private AdminAccessServiceImpl service;
    private AccessEntryEntity actorAccess;

    @BeforeEach
    void setUp() {
        service = new AdminAccessServiceImpl(
                accessEntryRepository,
                auditRepository,
                userIdentityRepository,
                identityProtectionService,
                googleAccessService
        );
        UUID actorUserId = UUID.randomUUID();
        UserIdentityEntity identity = new UserIdentityEntity();
        identity.setUserId(actorUserId);
        identity.setDisplayEmail(ACTOR_EMAIL);
        actorAccess = entry(ACTOR_EMAIL, AccessEntryEntity.Role.ADMIN, AccessEntryEntity.Status.ACTIVE);

        when(identityProtectionService.hashSubject(SUBJECT)).thenReturn(new byte[]{1});
        when(userIdentityRepository.findByProviderAndProviderSubjectHash(
                eq(UserIdentityEntity.Provider.GOOGLE),
                any(byte[].class)
        )).thenReturn(Optional.of(identity));
        when(googleAccessService.findActiveAccess(ACTOR_EMAIL)).thenReturn(Optional.of(actorAccess));
    }

    @Test
    void creatingAnAdminRequiresRecentAuthenticationAndExactTypedEmail() {
        when(googleAccessService.normalizeEmail("new-admin@example.com"))
                .thenReturn("new-admin@example.com");

        assertThatThrownBy(() -> service.create(
                SUBJECT,
                new CreateAdminAccessEntryRequestDto(
                        "new-admin@example.com",
                        AdminAccessRole.ADMIN,
                        "new-admin@example.com"
                ),
                false
        )).isInstanceOfSatisfying(AdminAccessException.class, exception ->
                assertThat(exception.problemCode()).isEqualTo("RECENT_AUTHENTICATION_REQUIRED"));

        verify(accessEntryRepository, never()).saveAndFlush(any());
    }

    @Test
    void anAdministratorCannotDemoteTheirOwnGrant() {
        when(accessEntryRepository.findById(actorAccess.getId())).thenReturn(Optional.of(actorAccess));
        when(googleAccessService.normalizeEmail(ACTOR_EMAIL)).thenReturn(ACTOR_EMAIL);

        assertThatThrownBy(() -> service.update(
                SUBJECT,
                actorAccess.getId(),
                new UpdateAdminAccessEntryRequestDto(
                        AdminAccessRole.USER,
                        AdminAccessStatus.ACTIVE,
                        actorAccess.getVersion(),
                        ACTOR_EMAIL
                ),
                true
        )).isInstanceOfSatisfying(AdminAccessException.class, exception ->
                assertThat(exception.problemCode()).isEqualTo("SELF_ADMIN_CHANGE_FORBIDDEN"));

        verify(accessEntryRepository, never()).saveAndFlush(any());
    }

    @Test
    void revokingAUserIsSoftAndAppendsAnAuditEvent() {
        AccessEntryEntity target = entry(
                "person@example.com",
                AccessEntryEntity.Role.USER,
                AccessEntryEntity.Status.ACTIVE
        );
        when(accessEntryRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(accessEntryRepository.saveAndFlush(target)).thenReturn(target);

        var updated = service.update(
                SUBJECT,
                target.getId(),
                new UpdateAdminAccessEntryRequestDto(
                        AdminAccessRole.USER,
                        AdminAccessStatus.REVOKED,
                        target.getVersion(),
                        null
                ),
                false
        );

        assertThat(updated.status()).isEqualTo(AdminAccessStatus.REVOKED);
        assertThat(target.getStatus()).isEqualTo(AccessEntryEntity.Status.REVOKED);
        ArgumentCaptor<AccessEntryAuditEventEntity> auditCaptor =
                ArgumentCaptor.forClass(AccessEntryAuditEventEntity.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction())
                .isEqualTo(AccessEntryAuditEventEntity.Action.REVOKED);
    }

    private AccessEntryEntity entry(
            String email,
            AccessEntryEntity.Role role,
            AccessEntryEntity.Status status
    ) {
        AccessEntryEntity entry = new AccessEntryEntity();
        entry.setId(UUID.randomUUID());
        entry.setNormalizedEmail(email);
        entry.setRole(role);
        entry.setStatus(status);
        entry.setCreatedAt(Instant.now());
        entry.setUpdatedAt(Instant.now());
        return entry;
    }
}
