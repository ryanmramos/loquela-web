package com.example.whatsanki.dto;

import com.example.whatsanki.domain.LookupSessionId;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliverLookupResultJobPayloadDtoTest {
    @Test
    void roundTripsExactIdOnlyPayload() {
        var expected = DeliverLookupResultJobPayloadDto.create(LookupSessionId.random());

        assertThat(DeliverLookupResultJobPayloadDto.from(expected.toMap())).isEqualTo(expected);
    }

    @Test
    void rejectsUnknownFieldsAndUnsupportedVersions() {
        Map<String, Object> extra = new LinkedHashMap<>(
                DeliverLookupResultJobPayloadDto.create(LookupSessionId.random()).toMap()
        );
        extra.put("body", "must not enter the queue");

        assertThatThrownBy(() -> DeliverLookupResultJobPayloadDto.from(extra))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeliverLookupResultJobPayloadDto.from(Map.of(
                "schemaVersion", 2,
                "lookupSessionId", LookupSessionId.random().value().toString()
        ))).isInstanceOf(IllegalArgumentException.class);
    }
}
