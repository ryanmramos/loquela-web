package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.dto.TimedCaptionCueDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RollingCaptionNormalizerTest {
    private final RollingCaptionNormalizer normalizer = new RollingCaptionNormalizer();

    @Test void collapsesExactRepeatUsingNewerDisplayText() {
        assertNormalized(cue(0, 0, 1_000, "hola mundo desde aquí"),
                cue(1, 900, 2_000, "Hola mundo desde aquí."), "Hola mundo desde aquí.");
    }
    @Test void collapsesFullGrowthWithoutLosingIntentionalRepeatedSpeech() {
        assertNormalized(cue(0, 0, 1_000, "quiero aprender"), cue(1, 900, 2_000, "quiero aprender español"), "quiero aprender español");
        assertNormalized(cue(0, 0, 1_000, "no"), cue(1, 900, 2_000, "no, no, no"), "no, no, no");
    }
    @Test void removesSlidingSuffixPrefixOverlap() {
        assertNormalized(cue(0, 1_000, 3_000, "Quiero aprender español porque"),
                cue(1, 2_500, 5_000, "aprender español porque viajaré pronto"),
                "Quiero aprender español porque viajaré pronto");
    }
    @Test void removesLongRollingCaptionOverlapWhenNewFrameAddsManyWords() {
        assertNormalized(
                cue(0, 1_000, 3_000, "Es que todos los incrementales, tío, sobre todo si está medianamente bien"),
                cue(1, 3_000, 5_000, "sobre todo si está medianamente bien hecho, es que son un vicio"),
                "Es que todos los incrementales, tío, sobre todo si está medianamente bien hecho, es que son un vicio");
        assertNormalized(
                cue(0, 1_000, 3_000, "esta época de ir trayendo, ahora que ya he terminado la velada, iré trayendo"),
                cue(1, 3_000, 5_000, "he terminado la velada, iré trayendo cositas de juegos"),
                "esta época de ir trayendo, ahora que ya he terminado la velada, iré trayendo cositas de juegos");
    }
    @Test void preservesAccumulatedTextWhenCurrentCueIsOnlyAnExistingSuffix() {
        assertNormalized(cue(0, 1_000, 3_000, "Tontos y más, de entretenimiento un poco más, no sé"),
                cue(1, 2_500, 5_000, "entretenimiento un poco más, no sé"),
                "Tontos y más, de entretenimiento un poco más, no sé");
    }
    @Test void comparesCasePunctuationAndUnicodeWords() {
        assertNormalized(cue(0, 0, 1_000, "ÉSTA es, una prueba útil"),
                cue(1, 900, 2_000, "es una prueba útil hoy."), "ÉSTA es, una prueba útil hoy.");
    }
    @Test void keepsInsufficientOverlapIntentionalShortRepeatAndTemporalGapSeparate() {
        assertSeparate(cue(0, 0, 1_000, "yes I know"), cue(1, 900, 2_000, "I know that"));
        assertSeparate(cue(0, 0, 1_000, "no, no, no"), cue(1, 900, 2_000, "No, no, no."));
        assertSeparate(cue(0, 0, 1_000, "one two three four"), cue(1, 3_001, 4_000, "two three four five"));
        assertSeparate(cue(0, 0, 1_000, "unrelated cue words"), cue(1, 900, 2_000, "something entirely different"));
    }

    private void assertNormalized(TimedCaptionCueDto first, TimedCaptionCueDto second, String expected) {
        var result = normalizer.normalize(List.of(first, second));
        assertThat(result.cues()).singleElement().extracting(TimedCaptionCueDto::text).isEqualTo(expected);
    }
    private void assertSeparate(TimedCaptionCueDto first, TimedCaptionCueDto second) {
        assertThat(normalizer.normalize(List.of(first, second)).cues()).hasSize(2);
    }
    private TimedCaptionCueDto cue(int ordinal, long start, long end, String text) {
        return new TimedCaptionCueDto(ordinal, start, end, text);
    }
}
