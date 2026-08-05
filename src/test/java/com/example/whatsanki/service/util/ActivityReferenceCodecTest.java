package com.example.whatsanki.service.util;

import com.example.whatsanki.dto.ActivityItemDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityReferenceCodecTest {
    @Test
    void roundTripsOwnedResourceReferencesAndNanosecondCursorOrdering() {
        UUID id = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-08-04T20:15:16.123456Z");

        String reference = ActivityReferenceCodec.encode(ActivityItemDto.Category.LOOKUP, id);
        String cursor = ActivityReferenceCodec.encodeCursor(updatedAt, ActivityItemDto.Category.LOOKUP, id);

        assertThat(ActivityReferenceCodec.decode(reference)).contains(
                new ActivityReferenceCodec.Reference(ActivityItemDto.Category.LOOKUP, id)
        );
        assertThat(ActivityReferenceCodec.decodeCursor(cursor)).contains(
                new ActivityReferenceCodec.Cursor(updatedAt, ActivityItemDto.Category.LOOKUP, id)
        );
    }

    @Test
    void rejectsMalformedReferencesAndCursors() {
        assertThat(ActivityReferenceCodec.decode("act_src_not-a-reference")).isEmpty();
        assertThat(ActivityReferenceCodec.decodeCursor("not-a-cursor")).isEmpty();
        assertThat(ActivityReferenceCodec.decodeCursor(" ")).isEmpty();
    }
}
