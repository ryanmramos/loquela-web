package com.example.whatsanki.service.storage;

import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.SourceDocumentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OriginalDocumentRetentionServiceImplTest {

    @Test
    void enqueuesExpiredOriginalWithoutDeletingReadyCorpus() {
        SourceDocumentRepository repository = mock(SourceDocumentRepository.class);
        SourceObjectCleanupScheduler scheduler = mock(SourceObjectCleanupScheduler.class);
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(UUID.randomUUID());
        source.setUserId(UUID.randomUUID());
        source.setStatus(SourceDocumentEntity.Status.READY);
        source.setObjectKey("internal/original");
        when(repository.findOriginalRetentionCandidates(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(source));
        OriginalDocumentRetentionServiceImpl service = new OriginalDocumentRetentionServiceImpl(
                repository, scheduler, configuration
        );
        Instant now = Instant.parse("2026-07-17T12:00:00Z");

        assertThat(service.enqueueExpiredOriginals(now)).isEqualTo(1);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findOriginalRetentionCandidates(cutoff.capture(), any(Pageable.class));
        assertThat(cutoff.getValue()).isEqualTo(now.minus(configuration.getRetention().getOriginalDocument()));
        verify(scheduler).enqueueIfNeeded(
                new com.example.whatsanki.domain.UserId(source.getUserId()),
                new com.example.whatsanki.domain.SourceId(source.getId())
        );
        assertThat(source.getStatus()).isEqualTo(SourceDocumentEntity.Status.READY);
    }
}
