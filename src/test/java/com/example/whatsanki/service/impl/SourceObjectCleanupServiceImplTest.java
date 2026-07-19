package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.service.ObjectStore;
import com.example.whatsanki.service.SourceObjectCleanupLifecycleService;
import com.example.whatsanki.service.util.ObjectKeyFactory;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class SourceObjectCleanupServiceImplTest {

    @Test
    void idempotentlyDeletesDeterministicObjectBeforeClearingMetadata() {
        SourceObjectCleanupLifecycleService lifecycle = mock(SourceObjectCleanupLifecycleService.class);
        ObjectStore objectStore = mock(ObjectStore.class);
        SourceObjectCleanupServiceImpl service = new SourceObjectCleanupServiceImpl(
                lifecycle, objectStore, new ObjectKeyFactory()
        );
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();

        service.purge(userId, sourceId);

        var ordered = inOrder(lifecycle, objectStore);
        ordered.verify(lifecycle).prepare(userId, sourceId);
        ordered.verify(objectStore).delete(
                "users/" + userId.value() + "/sources/" + sourceId.value() + "/original"
        );
        ordered.verify(lifecycle).complete(userId, sourceId);
    }
}
