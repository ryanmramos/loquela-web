package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.PassageDraftDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.storage.ObjectStore;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimedTranscriptIngestionServiceImplTest {
    private final TimedTranscriptLifecycleService lifecycle = mock(TimedTranscriptLifecycleService.class);
    private final TimedTranscriptPassageBuilder passageBuilder = mock(TimedTranscriptPassageBuilder.class);
    private final ObjectStore objectStore = mock(ObjectStore.class);
    private final WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
    private final TimedTranscriptIngestionServiceImpl service = new TimedTranscriptIngestionServiceImpl(
            List.of(new WebVttTimedTranscriptParser(new CaptionTextCleaner()),
                    new SrtTimedTranscriptParser(new CaptionTextCleaner())),
            lifecycle, new RollingCaptionNormalizer(), passageBuilder, objectStore, configuration);

    @Test
    void commitsMetadataAndPassagesBeforeDeletingTheRawUpload() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        String objectKey = "users/owner/sources/source/original";
        var origin = new OriginUrlPolicy.Origin("https://media.example/lesson", null, null);
        when(lifecycle.prepare(userId, sourceId)).thenReturn(new TimedTranscriptLifecycleService.Preparation(
                false, objectKey, TimedTranscriptFormat.VTT, origin, 1_000));
        when(objectStore.open(objectKey)).thenReturn(new ByteArrayInputStream(("""
                WEBVTT

                00:00:01.000 --> 00:00:03.000
                Quiero aprender español.
                """).getBytes(StandardCharsets.UTF_8)));
        when(passageBuilder.build(anyList(), eq(origin), eq(1_000L)))
                .thenReturn(List.of(new PassageDraftDto("Quiero aprender español.", Map.of(), 3)));

        service.ingest(userId, sourceId);

        var order = inOrder(lifecycle, objectStore);
        order.verify(lifecycle).storeParsedMetadata(eq(userId), eq(sourceId), any());
        order.verify(lifecycle).markIndexing(eq(userId), eq(sourceId), eq(0));
        order.verify(lifecycle).complete(eq(userId), eq(sourceId), anyList(), eq(24L), eq(3_000L));
        order.verify(objectStore).delete(objectKey);
        order.verify(lifecycle).clearTemporaryObjectKey(userId, sourceId);
    }

    @Test
    void aReadyRetryOnlyPerformsIdempotentObjectCleanup() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        String objectKey = "users/owner/sources/source/original";
        when(lifecycle.prepare(userId, sourceId)).thenReturn(new TimedTranscriptLifecycleService.Preparation(
                true, objectKey, TimedTranscriptFormat.SRT,
                new OriginUrlPolicy.Origin(null, null, null), 0));

        service.ingest(userId, sourceId);

        verify(objectStore).delete(objectKey);
        verify(lifecycle).clearTemporaryObjectKey(userId, sourceId);
    }
}
