package com.example.whatsanki.service.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserReturnPathTest {

    @Test
    void acceptsOnlyAuthenticatedApplicationRoutes() {
        assertThat(BrowserReturnPath.validate("/app/decks?sort=recent"))
                .isEqualTo("/app/decks?sort=recent");
        assertThat(BrowserReturnPath.validate(null)).isEqualTo("/app");
        assertThat(BrowserReturnPath.validate("/login")).isEqualTo("/app");
        assertThat(BrowserReturnPath.validate("//evil.example/app")).isEqualTo("/app");
        assertThat(BrowserReturnPath.validate("https://evil.example/app")).isEqualTo("/app");
        assertThat(BrowserReturnPath.validate("/app\\evil")).isEqualTo("/app");
        assertThat(BrowserReturnPath.validate("/app\r\nLocation: https://evil.example"))
                .isEqualTo("/app");
    }
}
