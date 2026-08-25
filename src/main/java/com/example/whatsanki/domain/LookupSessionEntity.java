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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Captures a lookup request, language pair, result snapshot, and expiration state. */
@Entity
@Table(name = "lookup_session")
@Getter
@Setter
@NoArgsConstructor
public class LookupSessionEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "inbound_message_id") private UUID inboundMessageId;
    @Column(name = "requested_term", nullable = false, length = 256) private String requestedTerm;
    @Column(name = "normalized_term", nullable = false, length = 256) private String normalizedTerm;
    @Column(name = "source_language", nullable = false, length = 35) private String sourceLanguage;
    @Column(name = "definition_language", nullable = false, length = 35) private String definitionLanguage;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "result_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> resultPayload = new LinkedHashMap<>();
    /** Filters are persisted separately from results so a queued browser lookup is immutable. */
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "request_filters", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> requestFilters = new LinkedHashMap<>();
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Status status;
    @Column(name = "error_code", length = 64) private String errorCode;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;

    @PrePersist
    void prePersist() {
        id = id == null ? UUID.randomUUID() : id;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public enum Status { PENDING, READY, FAILED, EXPIRED }
}
