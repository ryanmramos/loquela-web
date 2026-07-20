package com.example.whatsanki.dto;

import com.example.whatsanki.domain.LookupSessionId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LookupJobPayloadDtoTest {

    @Test
    void roundTripsAnIdOnlyVersionedPayload() {
        LookupJobPayloadDto expected = LookupJobPayloadDto.create(LookupSessionId.random());

        assertThat(LookupJobPayloadDto.from(expected.toMap())).isEqualTo(expected);
        assertThat(expected.toMap()).containsOnlyKeys("schemaVersion", "lookupSessionId");
    }

    @Test
    void rejectsContentAndMalformedIdentifiers() {
        assertThatThrownBy(() -> LookupJobPayloadDto.from(Map.of(
                "schemaVersion", 1,
                "lookupSessionId", LookupSessionId.random().value().toString(),
                "term", "must not be queued"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion and lookupSessionId");

        assertThatThrownBy(() -> LookupJobPayloadDto.from(Map.of(
                "schemaVersion", 1,
                "lookupSessionId", "not-a-uuid"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UUID");
    }
}
