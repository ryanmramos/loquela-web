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
@Table(name = "practice_session")
@Getter
@Setter
@NoArgsConstructor
public class PracticeSessionEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "deck_id", nullable = false) private UUID deckId;
    @Column(name = "deck_name", nullable = false, length = 255) private String deckName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Status status;
    @Column(name = "current_position", nullable = false) private int currentPosition;
    @Column(name = "answer_revealed", nullable = false) private boolean answerRevealed;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "completed_at") private Instant completedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        id = id == null ? UUID.randomUUID() : id;
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public enum Status { ACTIVE, COMPLETED, EXPIRED }
}
