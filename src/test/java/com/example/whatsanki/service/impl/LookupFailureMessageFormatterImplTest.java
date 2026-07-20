package com.example.whatsanki.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LookupFailureMessageFormatterImplTest {
    private final LookupFailureMessageFormatterImpl formatter = new LookupFailureMessageFormatterImpl();

    @Test
    void providesSpecificSafeGuidanceForActionableFailures() {
        assertThat(formatter.format("DEFINITION_NOT_FOUND"))
                .contains("couldn't find a definition");
        assertThat(formatter.format("CONTEXT_NOT_FOUND"))
                .contains("no examples in your ready sources")
                .contains("*ADD*");
        assertThat(formatter.format("UNSUPPORTED_DICTIONARY_PAIR"))
                .contains("isn't supported")
                .contains("*SETTINGS*")
                .contains("*LANGUAGE*");
        assertThat(formatter.format("PROVIDER_TIMEOUT"))
                .contains("timed out after retrying")
                .contains("*LOOKUP*");
    }

    @Test
    void hidesUnknownInternalFailuresBehindGenericGuidance() {
        assertThat(formatter.format("PROVIDER_LANGUAGE_MISMATCH"))
                .isEqualTo(LookupFailureMessageFormatterImpl.GENERIC_FAILURE_MESSAGE)
                .doesNotContain("PROVIDER_LANGUAGE_MISMATCH");
    }
}
