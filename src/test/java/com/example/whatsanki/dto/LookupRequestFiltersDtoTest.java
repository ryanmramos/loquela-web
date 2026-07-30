package com.example.whatsanki.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LookupRequestFiltersDtoTest {

    @Test
    void roundTripsAStableJsonSafeLookupScope() {
        UUID first = UUID.fromString("00000000-0000-4000-8000-000000000002");
        UUID second = UUID.fromString("00000000-0000-4000-8000-000000000001");
        LookupRequestFiltersDto filters = new LookupRequestFiltersDto(
                Set.of(first, second),
                Set.of("YOUTUBE_VIDEO", "PDF")
        );

        var stored = filters.toMap();

        assertThat(stored.get("sourceIds")).isEqualTo(List.of(second.toString(), first.toString()));
        assertThat(stored.get("sourceKinds")).isEqualTo(List.of("PDF", "YOUTUBE_VIDEO"));
        assertThat(LookupRequestFiltersDto.fromMap(stored)).isEqualTo(filters);
    }
}
