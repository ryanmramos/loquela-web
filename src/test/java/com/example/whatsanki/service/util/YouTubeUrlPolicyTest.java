package com.example.whatsanki.service.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YouTubeUrlPolicyTest {
    private final YouTubeUrlPolicy policy = new YouTubeUrlPolicy();

    @Test
    void canonicalizesSupportedWatchAndShortUrls() {
        assertThat(policy.parse("https://youtu.be/dQw4w9WgXcQ").canonicalUrl().toString())
                .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(policy.parse("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=10s").videoId())
                .isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void acceptsAndDiscardsRecognizedMobileShareParameters() {
        assertThat(policy.parse("https://youtu.be/MC10Sg-jS3I?is=JyOewx9KFqMfvV3f").canonicalUrl().toString())
                .isEqualTo("https://www.youtube.com/watch?v=MC10Sg-jS3I");
        assertThat(policy.parse("https://youtu.be/dQw4w9WgXcQ?si=share-token&t=30s").videoId())
                .isEqualTo("dQw4w9WgXcQ");
        assertThat(policy.parse(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=shared&pp=share-token"
        ).videoId()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void rejectsNonHttpsHostsAndPlaylistParameters() {
        assertThatThrownBy(() -> policy.parse("http://youtube.com/watch?v=dQw4w9WgXcQ"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.parse("https://evil.example/watch?v=dQw4w9WgXcQ"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.parse("https://youtube.com/watch?v=dQw4w9WgXcQ&list=PL123"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.parse("https://youtu.be/dQw4w9WgXcQ?index=2"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownEmptyAndDuplicateQueryParameters() {
        assertThatThrownBy(() -> policy.parse("https://youtube.com/watch?v=dQw4w9WgXcQ&v=aaaaaaaaaaa"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.parse("https://youtu.be/dQw4w9WgXcQ?unknown=value"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.parse("https://youtube.com/watch?v=dQw4w9WgXcQ&unknown=value"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.parse("https://youtu.be/dQw4w9WgXcQ?si="))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.parse("https://youtu.be/dQw4w9WgXcQ?si=one&"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.parse("https://youtu.be/dQw4w9WgXcQ?si=one&si=two"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
