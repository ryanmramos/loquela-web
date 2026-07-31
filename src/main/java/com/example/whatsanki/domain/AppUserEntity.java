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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUserEntity {

    @Id
    private UUID id;

    @Column(name = "source_language", nullable = false, length = 35)
    private String sourceLanguage;

    @Column(name = "definition_language", nullable = false, length = 35)
    private String definitionLanguage;

    @Column(name = "interface_language", length = 2)
    private String interfaceLanguage;

    @Column(name = "automatic_caption_fallback", nullable = false)
    private boolean automaticCaptionFallback = true;

    @Column(name = "export_deck_prefix", nullable = false, length = 100)
    private String exportDeckPrefix = "Loquela — ";

    @Column(name = "export_include_provenance", nullable = false)
    private boolean exportIncludeProvenance = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        id = id == null ? UUID.randomUUID() : id;
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = updatedAt == null ? now : updatedAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public enum Status { ACTIVE, DISABLED, DELETING }
}
