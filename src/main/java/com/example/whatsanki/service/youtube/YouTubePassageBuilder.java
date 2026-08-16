package com.example.whatsanki.service.youtube;

import com.example.whatsanki.dto.PassageDraftDto;
import com.example.whatsanki.dto.TimedCaptionCueDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Deprecated(forRemoval = true)
public class YouTubePassageBuilder {
    private final TextNormalizer textNormalizer;
    private final WhatsAnkiConfigurationDto configuration;

    public List<PassageDraftDto> build(String videoId, String channel, List<TimedCaptionCueDto> cues) {
        List<PassageDraftDto> passages = new ArrayList<>();
        for (int index = 0; index < cues.size(); ) {
            int startIndex = index;
            StringBuilder text = new StringBuilder();
            int tokenCount = 0;
            while (index < cues.size() && index - startIndex < configuration.getIngestion().getMaxPassageSentences()) {
                String cueText = textNormalizer.normalizeDisplay(cues.get(index).text());
                int cueTokens = tokenCount(cueText);
                int proposedLength = text.length() + (text.isEmpty() ? 0 : 1) + cueText.length();
                if (!text.isEmpty() && (proposedLength > configuration.getIngestion().getMaxPassageCharacters()
                        || tokenCount + cueTokens > configuration.getIngestion().getMaxPassageTokens())) {
                    break;
                }
                if (!text.isEmpty()) text.append(' ');
                text.append(cueText);
                tokenCount += cueTokens;
                index++;
            }
            if (index == startIndex) index++;
            TimedCaptionCueDto first = cues.get(startIndex);
            TimedCaptionCueDto last = cues.get(index - 1);
            long playbackStart = Math.max(0L, first.startMs() - configuration.getYoutube().getPlaybackLead().toMillis());
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("provider", "YOUTUBE");
            location.put("videoId", videoId);
            location.put("startMs", first.startMs());
            location.put("endMs", last.endMs());
            location.put("playbackStartMs", playbackStart);
            if (channel != null && !channel.isBlank()) location.put("channelName", channel);
            passages.add(new PassageDraftDto(text.toString(), location, Math.max(tokenCount, 1)));
        }
        return List.copyOf(passages);
    }

    private int tokenCount(String text) {
        String normalized = textNormalizer.normalizeForSearch(text);
        return normalized.isBlank() ? 0 : normalized.split("\\s+").length;
    }
}
