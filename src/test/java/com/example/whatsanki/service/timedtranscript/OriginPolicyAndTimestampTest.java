package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.TimedTranscriptException;
import com.example.whatsanki.service.util.YouTubeUrlPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OriginPolicyAndTimestampTest {
    private final OriginUrlPolicy policy = new OriginUrlPolicy(new YouTubeUrlPolicy());

    @Test
    void canonicalizesYouTubeAndDiscardsIncomingSeekParameters() {
        var origin = policy.validate("https://youtu.be/dQw4w9WgXcQ?t=99");

        assertThat(origin.canonicalUrl()).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(origin.provider()).isEqualTo("YOUTUBE");
        assertThat(origin.providerId()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void acceptsGenericHttpsWithoutTreatingItAsAProvider() {
        var origin = policy.validate("https://media.example/lesson?id=42");

        assertThat(origin.canonicalUrl()).isEqualTo("https://media.example/lesson?id=42");
        assertThat(origin.provider()).isNull();
        assertThat(origin.providerId()).isNull();
    }

    @Test
    void rejectsUnsafeOrAmbiguousOrigins() {
        assertThatThrownBy(() -> policy.validate("http://media.example/lesson"))
                .isInstanceOfSatisfying(TimedTranscriptException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("TIMED_TRANSCRIPT_ORIGIN_INVALID"));
        assertThatThrownBy(() -> policy.validate("https://user:secret@media.example/lesson"))
                .isInstanceOf(TimedTranscriptException.class);
        assertThatThrownBy(() -> policy.validate("https://youtube.com/watch?v=bad"))
                .isInstanceOf(TimedTranscriptException.class);
    }

    @Test
    void appliesOffsetAndClampsShortPlaybackLeadIn() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.getTimedTranscript().setPlaybackLead(Duration.ofSeconds(3));
        OriginTimestampResolver resolver = new OriginTimestampResolver(configuration);

        assertThat(resolver.resolve(10_000, 2_000))
                .isEqualTo(new OriginTimestampResolver.Timestamp(12_000, 9_000));
        assertThat(resolver.resolve(1_000, 0))
                .isEqualTo(new OriginTimestampResolver.Timestamp(1_000, 0));
    }
}
