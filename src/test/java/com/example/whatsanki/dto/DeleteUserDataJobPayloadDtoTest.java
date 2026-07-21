package com.example.whatsanki.dto;

import com.example.whatsanki.domain.UserId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeleteUserDataJobPayloadDtoTest {

    @Test
    void roundTripsTheOwningUserId() {
        UserId userId = UserId.random();

        assertThat(DeleteUserDataJobPayloadDto.from(DeleteUserDataJobPayloadDto.create(userId).toMap()).userId())
                .isEqualTo(userId);
    }

    @Test
    void rejectsPayloadsThatContainAnythingBeyondTheVersionAndUserId() {
        assertThatThrownBy(() -> DeleteUserDataJobPayloadDto.from(Map.of(
                "schemaVersion", 1,
                "userId", UserId.random().value().toString(),
                "text", "must not be queued"
        ))).isInstanceOf(IllegalArgumentException.class);
    }
}
