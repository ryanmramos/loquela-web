package com.example.whatsanki.service.youtube;

import com.example.whatsanki.dto.YouTubeSubtitleResultDto;

public interface YouTubeSubtitleProvider {
    YouTubeSubtitleResultDto fetch(String videoId, String sourceLanguage);
}
