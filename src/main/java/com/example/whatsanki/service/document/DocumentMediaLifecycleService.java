package com.example.whatsanki.service.document;

import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DetectedDocumentTypeDto;
import com.example.whatsanki.dto.MediaFetchPreparationDto;
import com.example.whatsanki.dto.StoredObjectMetadataDto;

public interface DocumentMediaLifecycleService {
    MediaFetchPreparationDto prepare(UserId userId, SourceId sourceId);

    CompletionOutcome complete(
            UserId userId,
            SourceId sourceId,
            String objectKey,
            DetectedDocumentTypeDto detectedType,
            StoredObjectMetadataDto metadata
    );

    boolean markFailed(UserId userId, SourceId sourceId, String errorCode, boolean clearStaging);

    enum CompletionOutcome { STORED, DELETING, SOURCE_MISSING }
}
