package com.example.whatsanki.service.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserReferenceCodecTest {

    @Test
    void roundTripsAnOpaqueUuidReference() {
        UUID id = UUID.fromString("00000000-0000-4000-8000-000000000001");

        String reference = BrowserReferenceCodec.encode(id);

        assertThat(reference).hasSize(22).doesNotContain("=", id.toString());
        assertThat(BrowserReferenceCodec.decode(reference)).contains(id);
    }

    @Test
    void rejectsMalformedOrNonCanonicalReferences() {
        assertThat(BrowserReferenceCodec.decode(null)).isEmpty();
        assertThat(BrowserReferenceCodec.decode("")).isEmpty();
        assertThat(BrowserReferenceCodec.decode(" invalid-reference    ")).isEmpty();
        assertThat(BrowserReferenceCodec.decode("AAAAAAAAAAAAAAAAAAAAA!")).isEmpty();
    }
}
