package com.example.whatsanki.dto;

import com.example.whatsanki.domain.SourceId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeleteSourceJobPayloadDtoTest {

    @Test
    void roundTripsTheVersionedIdOnlyPayload() {
        DeleteSourceJobPayloadDto payload = DeleteSourceJobPayloadDto.create(SourceId.random());

        assertThat(DeleteSourceJobPayloadDto.from(payload.toMap())).isEqualTo(payload);
        assertThat(payload.toMap()).containsOnlyKeys("schemaVersion", "sourceId");
    }

    @Test
    void rejectsUnknownFieldsAndInvalidIdentifiers() {
        assertThatThrownBy(() -> DeleteSourceJobPayloadDto.from(Map.of(
                "schemaVersion", 1,
                "sourceId", SourceId.random().value().toString(),
                "text", "must never enter a job"
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeleteSourceJobPayloadDto.from(Map.of(
                "schemaVersion", 1,
                "sourceId", "invalid"
        ))).isInstanceOf(IllegalArgumentException.class);
    }
}
