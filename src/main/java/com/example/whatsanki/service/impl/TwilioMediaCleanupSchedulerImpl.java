package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DeleteTwilioMediaJobPayloadDto;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.service.BackgroundJobService;
import com.example.whatsanki.service.TwilioMediaCleanupScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioMediaCleanupSchedulerImpl implements TwilioMediaCleanupScheduler {

    private final InboundMessageRepository inboundMessageRepository;
    private final BackgroundJobRepository backgroundJobRepository;
    private final BackgroundJobService backgroundJobService;

    @Override
    @Transactional
    public void enqueueIfNeeded(UserId userId, InboundMessageId inboundMessageId) {
        var inbound = inboundMessageRepository.findByIdAndUserId(inboundMessageId.value(), userId.value());
        if (inbound.isEmpty() || inbound.orElseThrow().getTwilioMediaSid() == null) {
            return;
        }
        boolean active = backgroundJobRepository.existsActiveForInboundMessageAndType(
                userId.value(),
                inboundMessageId.value().toString(),
                BackgroundJobEntity.Type.DELETE_TWILIO_MEDIA.name()
        );
        if (active) {
            return;
        }
        var jobId = backgroundJobService.enqueue(
                userId,
                BackgroundJobEntity.Type.DELETE_TWILIO_MEDIA,
                DeleteTwilioMediaJobPayloadDto.create(inboundMessageId).toMap()
        );
        log.info("Enqueued Twilio media cleanup jobId={} userId={} inboundMessageId={}",
                jobId.value(), userId.value(), inboundMessageId.value());
    }

    @Override
    @Transactional
    public int enqueuePending(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        var candidates = inboundMessageRepository.findPendingTwilioMediaCleanup(PageRequest.of(0, limit));
        candidates.forEach(inbound -> enqueueIfNeeded(
                new UserId(inbound.getUserId()), new InboundMessageId(inbound.getId())
        ));
        return candidates.size();
    }
}
