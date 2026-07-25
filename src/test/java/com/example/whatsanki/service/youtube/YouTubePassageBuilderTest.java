package com.example.whatsanki.service.youtube;

import com.example.whatsanki.dto.TimedCaptionCueDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.util.TextNormalizerImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class YouTubePassageBuilderTest {
    private final YouTubePassageBuilder builder = new YouTubePassageBuilder(
            new TextNormalizerImpl(), new WhatsAnkiConfigurationDto()
    );

    @Test
    void groupsCaptionCuesAndPreservesTimestampedProvenanceWithLead() {
        var passages = builder.build("dQw4w9WgXcQ", "Spanish School", List.of(
                new TimedCaptionCueDto(2_000, 4_000, "Quiero"),
                new TimedCaptionCueDto(4_000, 6_000, "vivir aquí."),
                new TimedCaptionCueDto(7_000, 9_000, "Siguiente frase.")
        ));

        assertThat(passages).hasSize(1);
        assertThat(passages.get(0).displayText()).isEqualTo("Quiero vivir aquí. Siguiente frase.");
        assertThat(passages.get(0).location())
                .containsEntry("provider", "YOUTUBE")
                .containsEntry("videoId", "dQw4w9WgXcQ")
                .containsEntry("startMs", 2_000L)
                .containsEntry("endMs", 9_000L)
                .containsEntry("playbackStartMs", 0L)
                .containsEntry("channelName", "Spanish School");
    }
}
