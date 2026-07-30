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

import java.util.UUID;

@Entity
@Table(name = "practice_session_item")
@Getter
@Setter
@NoArgsConstructor
public class PracticeSessionItemEntity {
    @Id private UUID id;
    @Column(name = "session_id", nullable = false) private UUID sessionId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "card_id", nullable = false) private UUID cardId;
    @Column(nullable = false) private int position;
    @Enumerated(EnumType.STRING) @Column(name = "note_type", nullable = false, length = 64)
    private CardEntity.NoteType noteType;
    @Column(nullable = false, columnDefinition = "text") private String term;
    @Column(nullable = false, columnDefinition = "text") private String definition;
    @Column(name = "example_text", columnDefinition = "text") private String exampleText;
    @Column(name = "source_name", columnDefinition = "text") private String sourceName;
    @Column(name = "source_locator", length = 128) private String sourceLocator;
    @Column(name = "video_id", length = 11) private String videoId;
    @Column(name = "video_start_seconds") private Long videoStartSeconds;
}
