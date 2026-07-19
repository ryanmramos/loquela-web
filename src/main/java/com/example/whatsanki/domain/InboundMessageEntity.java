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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbound_message")
@Getter
@Setter
@NoArgsConstructor
public class InboundMessageEntity {
    @Id private UUID id;
    @Column(name = "twilio_message_sid", nullable = false, unique = true, length = 64) private String twilioMessageSid;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "body_ciphertext") private byte[] bodyCiphertext;
    @Column(name = "media_url_ciphertext") private byte[] mediaUrlCiphertext;
    @Column(name = "media_content_type") private String mediaContentType;
    @Column(name = "twilio_media_sid", length = 34) private String twilioMediaSid;
    @Enumerated(EnumType.STRING) @Column(name = "command_type", length = 32) private CommandType commandType;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;
    @Column(name = "purge_after") private Instant purgeAfter;

    @PrePersist
    void prePersist() {
        id = id == null ? UUID.randomUUID() : id;
        receivedAt = receivedAt == null ? Instant.now() : receivedAt;
    }

    public enum CommandType {
        HELP, SETTINGS, LANGUAGE, ADD, LOOKUP, SAVE, SOURCES, DELETE_SOURCE,
        EXPORT, STATUS, DELETE_MY_DATA, DOCUMENT, UNKNOWN
    }
}
