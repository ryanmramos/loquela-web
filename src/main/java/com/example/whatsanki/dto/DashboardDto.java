package com.example.whatsanki.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DashboardDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant generatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LanguagePair languagePair,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CardSummary cards,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SourceSummary sources,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        StorageSummary storage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        JobSummary jobs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ActivitySummary activity
) {
    public DashboardDto {
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(languagePair, "languagePair must not be null");
        Objects.requireNonNull(cards, "cards must not be null");
        Objects.requireNonNull(sources, "sources must not be null");
        Objects.requireNonNull(storage, "storage must not be null");
        Objects.requireNonNull(jobs, "jobs must not be null");
        Objects.requireNonNull(activity, "activity must not be null");
    }

    public record LanguagePair(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sourceLanguage,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String definitionLanguage
    ) {
        public LanguagePair {
            Objects.requireNonNull(sourceLanguage, "sourceLanguage must not be null");
            Objects.requireNonNull(definitionLanguage, "definitionLanguage must not be null");
        }
    }

    public record CardSummary(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            SectionStatus status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            Long cardsToStudy,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            Long totalCards,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            Long newCardsThisWeek
    ) {
        public static CardSummary unavailable() {
            return new CardSummary(SectionStatus.UNAVAILABLE, null, null, null);
        }
    }

    public record SourceSummary(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            SectionStatus status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            Long totalSources,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            Long readySources,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            Long processingSources
    ) {
        public static SourceSummary unavailable() {
            return new SourceSummary(SectionStatus.UNAVAILABLE, null, null, null);
        }
    }

    public record JobSummary(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SectionStatus status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long activeJobs
    ) {
        public static JobSummary unavailable() {
            return new JobSummary(SectionStatus.UNAVAILABLE, null);
        }
    }

    public record StorageSummary(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            SectionStatus status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            Long usedBytes
    ) {
        public static StorageSummary unavailable() {
            return new StorageSummary(SectionStatus.UNAVAILABLE, null);
        }
    }

    public record ActivitySummary(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SectionStatus status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ActivityItem> items
    ) {
        public ActivitySummary {
            items = items == null ? List.of() : List.copyOf(items);
        }

        public static ActivitySummary unavailable() {
            return new ActivitySummary(SectionStatus.UNAVAILABLE, List.of());
        }
    }

    public record ActivityItem(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ActivityKind kind,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String title,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String detail,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant occurredAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String target
    ) {
        public ActivityItem {
            Objects.requireNonNull(kind, "kind must not be null");
            Objects.requireNonNull(title, "title must not be null");
            Objects.requireNonNull(detail, "detail must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            Objects.requireNonNull(target, "target must not be null");
        }
    }

    public enum SectionStatus {
        AVAILABLE,
        UNAVAILABLE
    }

    public enum ActivityKind {
        SOURCE,
        JOB
    }
}
