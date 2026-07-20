package com.example.whatsanki.service.export;

import com.example.whatsanki.domain.ExportRequestEntity;
import com.example.whatsanki.domain.ExportRequestId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.ExportRequestRepository;
import com.example.whatsanki.service.storage.ObjectStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportArtifactRetentionServiceImplTest {
    @Test
    void deletesExpiredObjectBeforeMarkingRowExpired() {
        ExportRequestRepository repository = mock(ExportRequestRepository.class);
        ObjectStore objectStore = mock(ObjectStore.class);
        DeckExportLifecycleService lifecycle = mock(DeckExportLifecycleService.class);
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        ExportRequestEntity export = new ExportRequestEntity();
        export.setId(UUID.randomUUID());
        export.setUserId(UUID.randomUUID());
        export.setArtifactKey("users/u/exports/e/deck.apkg");
        export.setState(ExportRequestEntity.State.READY);
        Instant now = Instant.now();
        when(repository.findAllByStateAndExpiresAtLessThanEqualOrderByExpiresAt(
                eq(ExportRequestEntity.State.READY), eq(now), any(Pageable.class)
        )).thenReturn(List.of(export));
        when(lifecycle.markExpiredAfterDeletion(
                new UserId(export.getUserId()), new ExportRequestId(export.getId()), now
        )).thenReturn(true);
        var service = new ExportArtifactRetentionServiceImpl(
                repository, objectStore, lifecycle, configuration
        );

        assertThat(service.deleteExpiredArtifacts(now)).isEqualTo(1);

        verify(objectStore).delete(export.getArtifactKey());
        verify(lifecycle).markExpiredAfterDeletion(
                new UserId(export.getUserId()), new ExportRequestId(export.getId()), now
        );
    }
}
