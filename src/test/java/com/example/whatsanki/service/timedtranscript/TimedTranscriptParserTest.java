package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.dto.TimedCaptionCueDto;
import com.example.whatsanki.service.exception.TimedTranscriptException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimedTranscriptParserTest {
    private final CaptionTextCleaner cleaner = new CaptionTextCleaner();
    private final TimedTranscriptParser vtt = new WebVttTimedTranscriptParser(cleaner);
    private final TimedTranscriptParser srt = new SrtTimedTranscriptParser(cleaner);

    @Test
    void equivalentVttAndSrtProduceEquivalentSemanticCues() {
        var vttResult = vtt.parse("\uFEFFWEBVTT\r\n\r\nintro\r\n00:00:01.000 --> 00:00:03.200 align:start\r\n<v Speaker>Bonjour &amp; bienvenue.</v>\r\n", 10, 100, 10_000);
        var srtResult = srt.parse("14\n00:00:01,000 --> 00:00:03,200\n<v Speaker>Bonjour &amp; bienvenue.</v>\n", 10, 100, 10_000);

        assertThat(vttResult.cues()).containsExactly(new TimedCaptionCueDto(0, 1_000, 3_200, "Bonjour & bienvenue."));
        assertThat(srtResult.cues()).isEqualTo(vttResult.cues());
        assertThat(vttResult.strippedMarkupCount()).isEqualTo(1);
    }

    @Test
    void vttIgnoresStructuralBlocksAndKeepsOverlappingCues() {
        String input = """
                WEBVTT

                NOTE generated captions
                private note

                STYLE
                ::cue { color: lime }

                REGION
                id:fred

                00:00:01.000 --> 00:00:03.000
                uno

                00:00:02.000 --> 00:00:04.000
                dos
                """;
        var result = vtt.parse(input, 10, 100, 10_000);
        assertThat(result.cues()).extracting(TimedCaptionCueDto::text).containsExactly("uno", "dos");
        assertThat(result.ignoredStructuralBlockCount()).isEqualTo(3);
    }

    @Test
    void srtAllowsNumberingGapsAndPeriodMilliseconds() {
        var result = srt.parse("1\n00:00:00.000 --> 00:00:01.000\none\n\n9\n00:00:01,000 --> 00:00:02,000\ntwo\n", 10, 100, 10_000);
        assertThat(result.cues()).extracting(TimedCaptionCueDto::ordinal).containsExactly(0, 1);
    }

    @Test
    void rejectsBackwardTimeInvalidIntervalsAndAllLimits() {
        assertCode(vtt, "WEBVTT\n\n00:00:02.000 --> 00:00:03.000\na\n\n00:00:01.000 --> 00:00:04.000\nb\n", "TIMED_TRANSCRIPT_MALFORMED", 10, 100, 10_000);
        assertCode(srt, "1\n00:00:01,000 --> 00:00:01,000\na\n", "TIMED_TRANSCRIPT_MALFORMED", 10, 100, 10_000);
        assertCode(vtt, "WEBVTT\n\n00:00:00.000 --> 00:00:01.000\na\n\n00:00:01.000 --> 00:00:02.000\nb\n", "TIMED_TRANSCRIPT_CUE_LIMIT", 1, 100, 10_000);
        assertCode(srt, "1\n00:00:00,000 --> 00:00:01,000\ntoo long\n", "TIMED_TRANSCRIPT_TEXT_LIMIT", 10, 3, 10_000);
        assertCode(vtt, "WEBVTT\n\n00:00:00.000 --> 00:00:11.000\na\n", "TIMED_TRANSCRIPT_TIMELINE_LIMIT", 10, 100, 10_000);
    }

    @Test
    void unknownAngleBracketTextIsNotSilentlyRemoved() {
        var result = srt.parse("1\n00:00:00,000 --> 00:00:01,000\n2 < 3 and 5 > 4\n", 10, 100, 10_000);
        assertThat(result.cues().get(0).text()).isEqualTo("2 < 3 and 5 > 4");
    }

    @Test
    void stripsNestedCaptionTagsAndWebVttClasses() {
        var result = vtt.parse("WEBVTT\n\n00:00:00.000 --> 00:00:01.000\n<c.green><b>Hello</b></c> <00:00:00.500>world\n", 10, 100, 10_000);
        assertThat(result.cues().get(0).text()).isEqualTo("Hello world");
    }

    @Test
    void acceptsWhitespacePaddingBetweenTimingAndPayload() {
        var result = vtt.parse("WEBVTT\n\n00:00:00.000 --> 00:00:01.000\n \nhello\n", 10, 100, 10_000);

        assertThat(result.cues()).extracting(TimedCaptionCueDto::text).containsExactly("hello");
    }

    private void assertCode(TimedTranscriptParser parser, String input, String code,
                            int cues, long characters, long timeline) {
        assertThatThrownBy(() -> parser.parse(input, cues, characters, timeline))
                .isInstanceOfSatisfying(TimedTranscriptException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(code));
    }
}
