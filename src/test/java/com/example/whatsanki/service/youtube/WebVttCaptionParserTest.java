package com.example.whatsanki.service.youtube;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebVttCaptionParserTest {
    private final WebVttCaptionParser parser = new WebVttCaptionParser();

    @Test
    void removesMarkupDecodesEntitiesAndMergesRollingCaptions() {
        String vtt = """
                WEBVTT

                00:00:04.000 --> 00:00:06.000
                <c.color>Hola &amp;</c>

                00:00:05.500 --> 00:00:08.000 align:start
                <00:00:05.500><c>Hola &amp; mundo</c>

                00:01:02.000 --> 00:01:04.000
                Otra frase.
                """;

        var cues = parser.parse(vtt, 10);

        assertThat(cues).hasSize(2);
        assertThat(cues.get(0).startMs()).isEqualTo(4_000);
        assertThat(cues.get(0).endMs()).isEqualTo(8_000);
        assertThat(cues.get(0).text()).isEqualTo("Hola & mundo");
        assertThat(cues.get(1).startMs()).isEqualTo(62_000);
    }

    @Test
    void rejectsMalformedEmptyAndOverLimitDocuments() {
        assertThatThrownBy(() -> parser.parse("not vtt", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("""
                WEBVTT

                00:00:01.000 --> 00:00:02.000
                one

                00:00:03.000 --> 00:00:04.000
                two
                """, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
