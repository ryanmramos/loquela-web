package com.example.whatsanki.dto;

import com.example.whatsanki.domain.ExportRequestId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportDeckJobPayloadDtoTest {
    @Test
    void roundTripsIdOnlyPayload() {
        ExportDeckJobPayloadDto expected = ExportDeckJobPayloadDto.create(ExportRequestId.random());

        assertThat(ExportDeckJobPayloadDto.from(expected.toMap())).isEqualTo(expected);
        assertThat(expected.toMap()).containsOnlyKeys("schemaVersion", "exportRequestId");
    }

    @Test
    void rejectsContentAndMalformedIdentifiers() {
        assertThatThrownBy(() -> ExportDeckJobPayloadDto.from(Map.of(
                "schemaVersion", 1, "exportRequestId", "bad", "cards", "private"
        ))).isInstanceOf(IllegalArgumentException.class);
    }
}
