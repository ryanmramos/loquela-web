package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestTextJobPayloadDtoTest {

    @Test
    void roundTripsVersionedSourceReference() {
        IngestTextJobPayloadDto expected = IngestTextJobPayloadDto.create(SourceId.random());

        assertThat(IngestTextJobPayloadDto.from(expected.toMap())).isEqualTo(expected);
    }

    @Test
    void rejectsExtraContentAndInvalidSourceId() {
        assertThatThrownBy(() -> IngestTextJobPayloadDto.from(Map.of(
                "schemaVersion", 1,
                "sourceId", SourceId.random().value().toString(),
                "text", "must not be queued"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion and sourceId");

        assertThatThrownBy(() -> IngestTextJobPayloadDto.from(Map.of(
                "schemaVersion", 1,
                "sourceId", "not-a-uuid"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UUID");
    }
}
