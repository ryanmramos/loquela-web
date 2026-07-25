package com.example.whatsanki.dto;

import com.example.whatsanki.domain.VideoSourceEntity;

import java.time.Instant;
import java.util.List;

public record YouTubeSubtitleResultDto(
        String title,
        String channelName,
        Long durationMs,
        String captionLanguage,
        VideoSourceEntity.CaptionKind captionKind,
        String providerVersion,
        Instant retrievedAt,
        List<TimedCaptionCueDto> cues
) {
    public YouTubeSubtitleResultDto {
        if (title == null || title.isBlank() || captionLanguage == null || captionLanguage.isBlank()
                || captionKind == null || captionKind == VideoSourceEntity.CaptionKind.PENDING
                || providerVersion == null || providerVersion.isBlank() || retrievedAt == null
                || cues == null || cues.isEmpty()) {
            throw new IllegalArgumentException("YouTube subtitle result is incomplete");
        }
        title = title.strip();
        channelName = channelName == null ? "" : channelName.strip();
        cues = List.copyOf(cues);
    }
}
