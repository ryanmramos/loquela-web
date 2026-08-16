package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.PassageDraftDto;

import java.util.List;

public interface TimedTranscriptLifecycleService {
    Preparation prepare(UserId userId, SourceId sourceId);
    void storeParsedMetadata(UserId userId, SourceId sourceId, TimedTranscriptParseResult result);
    void markIndexing(UserId userId, SourceId sourceId, int coalescedCueCount);
    void complete(UserId userId, SourceId sourceId, List<PassageDraftDto> passages,
                  long normalizedCharacters, long timelineEndMs);
    void markFailed(UserId userId, SourceId sourceId, String errorCode);
    void clearTemporaryObjectKey(UserId userId, SourceId sourceId);

    record Preparation(boolean ready, String objectKey, TimedTranscriptFormat format,
                       OriginUrlPolicy.Origin origin, long timelineOffsetMs) { }
}
