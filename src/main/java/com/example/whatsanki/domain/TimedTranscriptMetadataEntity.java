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
@Table(name = "timed_transcript_metadata")
@Getter
@Setter
@NoArgsConstructor
public class TimedTranscriptMetadataEntity {
    @Id @Column(name = "source_id") private UUID sourceId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "original_filename", nullable = false, length = 512) private String originalFilename;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private Format format;
    @Column(nullable = false, length = 35) private String language;
    @Column(name = "origin_url", length = 2048) private String originUrl;
    @Enumerated(EnumType.STRING) @Column(name = "origin_provider", length = 24) private OriginProvider originProvider;
    @Column(name = "origin_provider_id", length = 32) private String originProviderId;
    @Column(name = "timeline_offset_ms", nullable = false) private long timelineOffsetMs;
    @Column(name = "cue_count", nullable = false) private int cueCount;
    @Column(name = "normalized_character_count", nullable = false) private long normalizedCharacterCount;
    @Column(name = "timeline_end_ms", nullable = false) private long timelineEndMs;
    @Column(name = "parser_version", nullable = false, length = 32) private String parserVersion;
    @Column(name = "normalizer_version", nullable = false, length = 32) private String normalizerVersion;
    @Column(name = "ignored_structural_block_count", nullable = false) private int ignoredStructuralBlockCount;
    @Column(name = "stripped_markup_count", nullable = false) private int strippedMarkupCount;
    @Column(name = "empty_cue_count", nullable = false) private int emptyCueCount;
    @Column(name = "coalesced_cue_count", nullable = false) private int coalescedCueCount;
    @Column(name = "temporary_object_key", length = 1024) private String temporaryObjectKey;

    public enum Format { VTT, SRT }
    public enum OriginProvider { YOUTUBE }
}
