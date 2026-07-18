package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FetchMediaJobPayloadDtoTest {

    @Test
    void roundTripsStrictIdOnlyPayload() {
        FetchMediaJobPayloadDto payload = FetchMediaJobPayloadDto.create(SourceId.random());

        assertThat(FetchMediaJobPayloadDto.from(payload.toMap())).isEqualTo(payload);
        assertThat(payload.toMap()).containsOnlyKeys("schemaVersion", "sourceId");
    }

    @Test
    void rejectsUnknownFieldsAndVersions() {
        assertThatThrownBy(() -> FetchMediaJobPayloadDto.from(Map.of(
                "schemaVersion", 1,
                "sourceId", SourceId.random().value().toString(),
                "mediaUrl", "secret"
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FetchMediaJobPayloadDto.from(Map.of(
                "schemaVersion", 2,
                "sourceId", SourceId.random().value().toString()
        ))).isInstanceOf(IllegalArgumentException.class);
    }
}
