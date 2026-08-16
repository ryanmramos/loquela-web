package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.dto.TimedCaptionCueDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.util.TextNormalizerImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimedTranscriptPassageBuilderTest {
    private final WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
    private final TimedTranscriptPassageBuilder builder = new TimedTranscriptPassageBuilder(
            new TextNormalizerImpl(), configuration, new OriginTimestampResolver(configuration));
    private final OriginUrlPolicy.Origin origin = new OriginUrlPolicy.Origin(
            "https://example.test/video", null, null);

    @Test
    void removesRollingCaptionOverlapWhenBuildingContext() {
        var passages = builder.build(List.of(
                cue(0, 1_000, 3_000,
                        "Es que todos los incrementales, tío, sobre todo si está medianamente bien"),
                cue(1, 3_000, 5_000,
                        "sobre todo si está medianamente bien hecho, es que son un vicio")), origin, 0);

        assertThat(passages).singleElement().extracting(draft -> draft.displayText())
                .isEqualTo("Es que todos los incrementales, tío, sobre todo si está medianamente bien hecho, es que son un vicio");
    }

    @Test
    void removesRollingCaptionOverlapAroundCaptionPlaceholders() {
        var passages = builder.build(List.of(
                cue(0, 1_000, 3_000, "y son [ __ ] super entretenimiento super"),
                cue(1, 3_000, 5_000, "[ __ ] super entretenimiento super vacío, tío")), origin, 0);

        assertThat(passages).singleElement().extracting(draft -> draft.displayText())
                .isEqualTo("y son [ __ ] super entretenimiento super vacío, tío");
    }

    @Test
    void removesShortSuffixOverlapWithoutCollapsingAnExactRepeatedCue() {
        var passages = builder.build(List.of(
                cue(0, 1_000, 3_000, "Ahí está y encima ganamos más dinero por hoja. Vale."),
                cue(1, 3_000, 5_000, "por hoja. Vale. Madre mía, esto es un vicio")), origin, 0);

        assertThat(passages).singleElement().extracting(draft -> draft.displayText())
                .isEqualTo("Ahí está y encima ganamos más dinero por hoja. Vale. Madre mía, esto es un vicio");

        var repeated = builder.build(List.of(
                cue(0, 1_000, 3_000, "no, no, no"),
                cue(1, 3_000, 5_000, "no, no, no")), origin, 0);
        assertThat(repeated).singleElement().extracting(draft -> draft.displayText())
                .isEqualTo("no, no, no no, no, no");
    }

    private TimedCaptionCueDto cue(int ordinal, long start, long end, String text) {
        return new TimedCaptionCueDto(ordinal, start, end, text);
    }
}
