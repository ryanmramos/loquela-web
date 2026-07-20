package com.example.whatsanki.service.storage;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.PurgeSourceObjectJobPayloadDto;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.service.job.BackgroundJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SourceObjectCleanupSchedulerImpl implements SourceObjectCleanupScheduler {

    private final BackgroundJobRepository backgroundJobRepository;
    private final BackgroundJobService backgroundJobService;

    @Override
    @Transactional
    public void enqueueIfNeeded(UserId userId, SourceId sourceId) {
        boolean active = backgroundJobRepository.existsActiveForSourceAndType(
                userId.value(),
                sourceId.value().toString(),
                BackgroundJobEntity.Type.PURGE_SOURCE_OBJECT.name()
        );
        if (active) {
            return;
        }
        var jobId = backgroundJobService.enqueue(
                userId,
                BackgroundJobEntity.Type.PURGE_SOURCE_OBJECT,
                PurgeSourceObjectJobPayloadDto.create(sourceId).toMap()
        );
        log.info("Enqueued source-object cleanup jobId={} userId={} sourceId={}",
                jobId.value(), userId.value(), sourceId.value());
    }
}
