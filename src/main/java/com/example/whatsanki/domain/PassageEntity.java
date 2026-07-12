package com.example.whatsanki.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "passage")
@Getter
@Setter
@NoArgsConstructor
public class PassageEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "source_id", nullable = false) private UUID sourceId;
    @Column(nullable = false) private int ordinal;
    @Column(name = "display_text", nullable = false, columnDefinition = "text") private String displayText;
    @Column(name = "normalized_text", nullable = false, columnDefinition = "text") private String normalizedText;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> location = new LinkedHashMap<>();
    @Column(name = "token_count", nullable = false) private int tokenCount;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @PrePersist
    void prePersist() {
        id = id == null ? UUID.randomUUID() : id;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
