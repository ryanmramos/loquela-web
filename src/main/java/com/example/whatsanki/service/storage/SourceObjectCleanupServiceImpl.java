package com.example.whatsanki.service.storage;

import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.service.storage.ObjectStore;
import com.example.whatsanki.service.storage.SourceObjectCleanupLifecycleService;
import com.example.whatsanki.service.storage.SourceObjectCleanupService;
import com.example.whatsanki.service.exception.ObjectStoreException;
import com.example.whatsanki.service.exception.SourceObjectCleanupException;
import com.example.whatsanki.service.util.ObjectKeyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SourceObjectCleanupServiceImpl implements SourceObjectCleanupService {

    private final SourceObjectCleanupLifecycleService lifecycleService;
    private final ObjectStore objectStore;
    private final ObjectKeyFactory objectKeyFactory;

    @Override
    public void purge(UserId userId, SourceId sourceId) {
        lifecycleService.prepare(userId, sourceId);
        String objectKey = objectKeyFactory.originalSource(userId, sourceId);
        try {
            objectStore.delete(objectKey);
        } catch (ObjectStoreException exception) {
            throw new SourceObjectCleanupException(
                    BackgroundJobFailureKind.TRANSIENT,
                    "SOURCE_OBJECT_DELETE_FAILED",
                    exception
            );
        }
        lifecycleService.complete(userId, sourceId);
        log.info("Purged original source object userId={} sourceId={}", userId.value(), sourceId.value());
    }
}
