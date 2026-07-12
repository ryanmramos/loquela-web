package com.example.whatsanki.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "export_request")
@Getter
@Setter
@NoArgsConstructor
public class ExportRequestEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "deck_id", nullable = false) private UUID deckId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private State state;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "snapshot_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> snapshotPayload = new LinkedHashMap<>();
    @Column(name = "snapshot_sha256", nullable = false) private byte[] snapshotSha256;
    @Column(name = "artifact_key", length = 1024) private String artifactKey;
    @Column(name = "artifact_sha256") private byte[] artifactSha256;
    @Column(name = "template_version", nullable = false, length = 64) private String templateVersion;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "error_code", length = 64) private String errorCode;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        id = id == null ? UUID.randomUUID() : id;
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = updatedAt == null ? now : updatedAt;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
    public enum State { QUEUED, RUNNING, READY, FAILED, EXPIRED }
}
