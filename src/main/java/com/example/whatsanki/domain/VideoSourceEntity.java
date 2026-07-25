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
@Table(name = "video_source")
@Getter
@Setter
@NoArgsConstructor
public class VideoSourceEntity {
    @Id @Column(name = "source_id") private UUID sourceId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Provider provider;
    @Column(name = "provider_video_id", nullable = false, length = 32) private String providerVideoId;
    @Column(name = "canonical_url", nullable = false, length = 512) private String canonicalUrl;
    @Column(length = 512) private String title;
    @Column(name = "channel_name", length = 512) private String channelName;
    @Column(name = "duration_ms") private Long durationMs;
    @Column(name = "caption_language", nullable = false, length = 35) private String captionLanguage;
    @Enumerated(EnumType.STRING) @Column(name = "caption_kind", nullable = false, length = 24) private CaptionKind captionKind;
    @Column(name = "provider_version", length = 64) private String providerVersion;
    @Column(name = "retrieved_at") private Instant retrievedAt;

    public enum Provider { YOUTUBE }
    public enum CaptionKind { PENDING, MANUAL, AUTOMATIC }
}
