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

@Entity
@Table(name = "card")
@Getter
@Setter
@NoArgsConstructor
public class CardEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "deck_id", nullable = false) private UUID deckId;
    @Enumerated(EnumType.STRING) @Column(name = "note_type", nullable = false, length = 64) private NoteType noteType;
    @Column(name = "source_language", nullable = false, length = 35) private String sourceLanguage;
    @Column(name = "definition_language", nullable = false, length = 35) private String definitionLanguage;
    @Column(nullable = false, columnDefinition = "text") private String term;
    @Column(nullable = false, columnDefinition = "text") private String definition;
    @Column(name = "example_text", columnDefinition = "text") private String exampleText;
    @Column(name = "source_id") private UUID sourceId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "source_location", columnDefinition = "jsonb")
    private Map<String, Object> sourceLocation;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = new LinkedHashMap<>();
    @Version @Column(nullable = false) private long version;
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
    public enum NoteType { BASIC, BASIC_CONTEXT }
}
