package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.SourceUploadEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.repository.SourceUploadRepository;
import com.example.whatsanki.repository.TimedTranscriptMetadataRepository;
import com.example.whatsanki.service.storage.ObjectStore;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Component
@RequiredArgsConstructor
@Slf4j
public class TimedTranscriptCleanupScheduler {
    private static final Pattern ORIGINAL_SOURCE_KEY = Pattern.compile(
            "users/([0-9a-fA-F-]{36})/sources/([0-9a-fA-F-]{36})/original");
    private final SourceUploadRepository uploadRepository;
    private final TimedTranscriptMetadataRepository metadataRepository;
    private final SourceDocumentRepository sourceRepository;
    private final BackgroundJobRepository jobRepository;
    private final ObjectStore objectStore;
    private final TimedTranscriptLifecycleService lifecycle;
    private final WhatsAnkiConfigurationDto configuration;

    @Scheduled(fixedDelayString = "${whatsanki.retention.scan-interval:PT1H}")
    public void sweep() {
        Instant now = Instant.now();
        for (SourceUploadEntity upload : uploadRepository.findAllByStateAndExpiresAtBefore(SourceUploadEntity.State.PENDING, now)) {
            upload.setState(SourceUploadEntity.State.EXPIRED);
            uploadRepository.save(upload);
        }
        for (var metadata : metadataRepository.findAllByTemporaryObjectKeyIsNotNull()) {
            var source = sourceRepository.findByIdAndUserId(metadata.getSourceId(), metadata.getUserId());
            if (source.isEmpty() || !isTerminal(source.orElseThrow().getStatus())) continue;
            boolean active = jobRepository.existsActiveForSourceAndType(metadata.getUserId(), metadata.getSourceId().toString(),
                    BackgroundJobEntity.Type.INGEST_TIMED_TRANSCRIPT.name());
            if (active) continue;
            try {
                objectStore.delete(metadata.getTemporaryObjectKey());
                lifecycle.clearTemporaryObjectKey(new UserId(metadata.getUserId()), new SourceId(metadata.getSourceId()));
            } catch (RuntimeException exception) {
                log.warn("Timed transcript cleanup will retry userId={} sourceId={}",
                        metadata.getUserId(), metadata.getSourceId());
            }
        }
        Instant orphanCutoff = now.minus(configuration.getIngestion().getUploadIntentLifetime());
        for (var object : objectStore.list("users/", configuration.getRetention().getScanBatchSize())) {
            if (object.lastModified() == null || object.lastModified().isAfter(orphanCutoff)) continue;
            Matcher matcher = ORIGINAL_SOURCE_KEY.matcher(object.objectKey());
            if (!matcher.matches()) continue;
            UUID userId;
            UUID sourceId;
            try { userId = UUID.fromString(matcher.group(1)); sourceId = UUID.fromString(matcher.group(2)); }
            catch (IllegalArgumentException exception) { continue; }
            if (sourceRepository.findByIdAndUserId(sourceId, userId).isEmpty()) {
                try { objectStore.delete(object.objectKey()); }
                catch (RuntimeException exception) { log.warn("Unreferenced source-object cleanup will retry"); }
            }
        }
    }

    private boolean isTerminal(SourceDocumentEntity.Status status) {
        return status == SourceDocumentEntity.Status.READY || status == SourceDocumentEntity.Status.FAILED;
    }
}
