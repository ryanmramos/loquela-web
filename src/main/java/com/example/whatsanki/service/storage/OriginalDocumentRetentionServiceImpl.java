package com.example.whatsanki.service.storage;

import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.storage.OriginalDocumentRetentionService;
import com.example.whatsanki.service.storage.SourceObjectCleanupScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class OriginalDocumentRetentionServiceImpl implements OriginalDocumentRetentionService {

    private final SourceDocumentRepository sourceDocumentRepository;
    private final SourceObjectCleanupScheduler cleanupScheduler;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    @Transactional
    public int enqueueExpiredOriginals(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        Instant cutoff = now.minus(configuration.getRetention().getOriginalDocument());
        var candidates = sourceDocumentRepository.findOriginalRetentionCandidates(
                cutoff,
                PageRequest.of(0, configuration.getRetention().getScanBatchSize())
        );
        candidates.forEach(source -> cleanupScheduler.enqueueIfNeeded(
                new UserId(source.getUserId()), new SourceId(source.getId())
        ));
        if (!candidates.isEmpty()) {
            log.info("Scheduled expired original-document cleanup count={} cutoff={}",
                    candidates.size(), cutoff);
        }
        return candidates.size();
    }
}
