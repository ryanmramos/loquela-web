package com.example.whatsanki.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Stores source lifecycle state while content moves from acquisition to indexed passages. */
@Entity
@Table(name = "source_document")
@Getter
@Setter
@NoArgsConstructor
public class SourceDocumentEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "inbound_message_id") private UUID inboundMessageId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private Kind kind;
    @Column(name = "display_name", nullable = false, length = 512) private String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Status status;
    @Column(name = "detected_media_type", length = 128) private String detectedMediaType;
    @Column(name = "object_key", length = 1024) private String objectKey;
    @Column(name = "staged_text_ciphertext") private byte[] stagedTextCiphertext;
    @Column(name = "original_stored_at") private Instant originalStoredAt;
    private byte[] sha256;
    @Column(length = 35) private String language;
    @Column(name = "character_count") private Long characterCount;
    @Column(name = "passage_count") private Integer passageCount;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();
    @Column(name = "error_code", length = 64) private String errorCode;
    @Column(name = "error_detail_safe", length = 512) private String errorDetailSafe;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        id = id == null ? UUID.randomUUID() : id;
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = updatedAt == null ? now : updatedAt;
    }

    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public enum Kind { PASTED_TEXT, TXT, PDF, EPUB, YOUTUBE_VIDEO, TIMED_TRANSCRIPT }
    /** Lifecycle states exposed to source status views and consumed by background workers. */
    public enum Status {
        RECEIVED, FETCHING, STORED, PARSING, NORMALIZING, EXTRACTING, INDEXING, READY, FAILED, DELETING, DELETED
    }
}
