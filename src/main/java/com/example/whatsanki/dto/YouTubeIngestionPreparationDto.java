package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceId;

public record YouTubeIngestionPreparationDto(
        SourceId sourceId,
        String videoId,
        String sourceLanguage,
        boolean automaticCaptionFallback,
        boolean alreadyReady
) { }
