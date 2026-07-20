package com.example.whatsanki.service.util;

import com.example.whatsanki.domain.ExportRequestId;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectKeyFactoryTest {

    @Test
    void buildsInternalIdOnlySourceKey() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();

        assertThat(new ObjectKeyFactory().originalSource(userId, sourceId))
                .isEqualTo("users/" + userId.value() + "/sources/" + sourceId.value() + "/original");
    }

    @Test
    void buildsInternalIdOnlyExportKey() {
        UserId userId = UserId.random();
        ExportRequestId exportRequestId = ExportRequestId.random();

        assertThat(new ObjectKeyFactory().exportedDeck(userId, exportRequestId))
                .isEqualTo("users/" + userId.value() + "/exports/"
                        + exportRequestId.value() + "/deck.apkg");
    }
}
