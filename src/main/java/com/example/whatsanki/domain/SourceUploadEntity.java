package com.example.whatsanki.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_upload")
@Getter
@Setter
@NoArgsConstructor
public class SourceUploadEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "display_name", nullable = false, length = 512) private String displayName;
    @Column(name = "declared_media_type", nullable = false, length = 128) private String declaredMediaType;
    @Column(name = "declared_size", nullable = false) private long declaredSize;
    @Column(name = "declared_sha256", nullable = false) private byte[] declaredSha256;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private State state;
    @Column(name = "source_id") private UUID sourceId;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Version @Column(nullable = false) private long version;

    @PrePersist
    void prePersist() {
        id = id == null ? UUID.randomUUID() : id;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        state = state == null ? State.PENDING : state;
    }

    public enum State { PENDING, COMPLETED, EXPIRED }
}
