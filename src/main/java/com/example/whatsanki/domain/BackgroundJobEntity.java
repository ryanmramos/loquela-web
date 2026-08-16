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
@Table(name = "background_job")
@Getter
@Setter
@NoArgsConstructor
public class BackgroundJobEntity {
    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private State state;

    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "run_after", nullable = false)
    private Instant runAfter;

    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        id = id == null ? UUID.randomUUID() : id;
        runAfter = runAfter == null ? now : runAfter;
        maxAttempts = maxAttempts == 0 ? 3 : maxAttempts;
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = updatedAt == null ? now : updatedAt;
    }
    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public enum Type {
        FETCH_MEDIA,
        EXTRACT_DOCUMENT,
        INGEST_TEXT,
        INGEST_TIMED_TRANSCRIPT,
        FETCH_VIDEO_SUBTITLES,
        LOOKUP,
        DELIVER_LOOKUP_RESULT,
        EXPORT_DECK,
        DELETE_TWILIO_MEDIA,
        PURGE_SOURCE_OBJECT,
        DELETE_SOURCE,
        DELETE_USER_DATA
    }

    public enum State {
        QUEUED, RUNNING, RETRY_WAIT, SUCCEEDED, DEAD, CANCELLED;

        public boolean canTransitionTo(State target) {
            return switch (this) {
                case QUEUED -> target == RUNNING || target == CANCELLED;
                case RUNNING -> target == SUCCEEDED
                        || target == RETRY_WAIT
                        || target == DEAD
                        || target == QUEUED;
                case RETRY_WAIT -> target == RUNNING || target == CANCELLED;
                case SUCCEEDED, DEAD, CANCELLED -> false;
            };
        }
    }
}
