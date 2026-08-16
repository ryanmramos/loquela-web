package com.example.whatsanki.service.youtube;

import com.example.whatsanki.dto.YouTubeSubtitleResultDto;

@Deprecated(forRemoval = true)
public interface YouTubeSubtitleProvider {
    YouTubeSubtitleResultDto fetch(String videoId, String sourceLanguage, boolean automaticCaptionFallback);
}
