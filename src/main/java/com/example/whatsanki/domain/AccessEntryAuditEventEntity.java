package com.example.whatsanki.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "access_entry_audit_event")
@Getter
@Setter
@NoArgsConstructor
public class AccessEntryAuditEventEntity {
    @Id
    private UUID id;

    @Column(name = "access_entry_id", nullable = false)
    private UUID accessEntryId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_role", length = 24)
    private AccessEntryEntity.Role previousRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_role", length = 24)
    private AccessEntryEntity.Role newRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 24)
    private AccessEntryEntity.Status previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 24)
    private AccessEntryEntity.Status newStatus;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        id = id == null ? UUID.randomUUID() : id;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public enum Action { BOOTSTRAPPED, CREATED, ROLE_CHANGED, REVOKED, REACTIVATED }
}

