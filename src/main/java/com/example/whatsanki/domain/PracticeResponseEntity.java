package com.example.whatsanki.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "practice_response")
@Getter
@Setter
@NoArgsConstructor
public class PracticeResponseEntity {
    @Id private UUID id;
    @Column(name = "session_id", nullable = false) private UUID sessionId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "card_id", nullable = false) private UUID cardId;
    @Column(nullable = false) private int position;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Rating rating;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public enum Rating { NEEDS_WORK, ALMOST, GOT_IT, EASY }
}
