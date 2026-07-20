package com.example.whatsanki.service.storage;

import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.storage.SourceObjectCleanupLifecycleService;
import com.example.whatsanki.service.exception.SourceObjectCleanupException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class SourceObjectCleanupLifecycleServiceImpl implements SourceObjectCleanupLifecycleService {

    private final SourceDocumentRepository sourceDocumentRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean prepare(UserId userId, SourceId sourceId) {
        var result = sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value());
        if (result.isEmpty()) {
            // The deterministic key can still contain an orphan left between S3 PUT and
            // the database completion transaction, so a missing source is safe to purge.
            return true;
        }
        SourceDocumentEntity source = result.orElseThrow();
        if (source.getStatus() != SourceDocumentEntity.Status.READY
                && source.getStatus() != SourceDocumentEntity.Status.FAILED
                && source.getStatus() != SourceDocumentEntity.Status.DELETING) {
            throw new SourceObjectCleanupException(
                    com.example.whatsanki.domain.BackgroundJobFailureKind.TRANSIENT,
                    "SOURCE_OBJECT_BUSY",
                    null
            );
        }
        return true;
    }

    @Override
    @Transactional
    public void complete(UserId userId, SourceId sourceId) {
        sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value())
                .ifPresent(source -> {
                    source.setObjectKey(null);
                    source.setSha256(null);
                    LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(source.getMetadata());
                    metadata.put("originalPurgedAt", Instant.now().toString());
                    metadata.put("mediaState", "PURGED");
                    source.setMetadata(metadata);
                    sourceDocumentRepository.save(source);
                });
    }
}
