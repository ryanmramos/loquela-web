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
@Table(name = "deck")
@Getter
@Setter
@NoArgsConstructor
public class DeckEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, length = 255) private String name;
    @Column(name = "normalized_name", nullable = false, length = 255) private String normalizedName;
    @Enumerated(EnumType.STRING) @Column(name = "theme_color", nullable = false, length = 24)
    private ThemeColor themeColor = ThemeColor.GREEN;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Status status;
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
    public enum Status { ACTIVE, ARCHIVED, DELETING }
    public enum ThemeColor { GREEN, ORANGE, BLUE, TEAL, PURPLE }
}
