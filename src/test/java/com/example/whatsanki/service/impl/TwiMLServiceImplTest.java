package com.example.whatsanki.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TwiMLServiceImplTest {

    private final TwiMLServiceImpl service = new TwiMLServiceImpl();

    @Test
    void createsTwilioMessageResponse() {
        assertThat(service.message("WhatsAnki commands:\nHELP"))
                .isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Response><Message>WhatsAnki commands:\nHELP</Message></Response>");
    }

    @Test
    void escapesUntrustedTextForXml() {
        assertThat(service.message("A < B & C > D"))
                .contains("A &lt; B &amp; C &gt; D")
                .doesNotContain("<Message>A < B");
    }
}
