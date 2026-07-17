package com.example.whatsanki.service.util;

import com.example.whatsanki.domain.SourceId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceReferenceCodecTest {

    @Test
    void roundTripsTheEntireSourceUuidWithoutPadding() {
        SourceId sourceId = SourceId.random();

        String reference = SourceReferenceCodec.encode(sourceId);

        assertThat(reference).hasSize(22).doesNotContain("=");
        assertThat(SourceReferenceCodec.decode(reference)).contains(sourceId);
    }

    @Test
    void rejectsMalformedOrWhitespaceModifiedReferences() {
        assertThat(SourceReferenceCodec.decode(null)).isEmpty();
        assertThat(SourceReferenceCodec.decode("not-a-source-id")).isEmpty();
        assertThat(SourceReferenceCodec.decode(" AAAAAAAAAAAAAAAAAAAAAA")).isEmpty();
        assertThat(SourceReferenceCodec.decode("AAAAAAAAAAAAAAAAAAAAAA ")).isEmpty();
    }
}
