package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.TwilioMediaDeletionPreparationDto;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.service.TwilioMediaCleanupLifecycleService;
import com.example.whatsanki.service.exception.TwilioMediaCleanupException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TwilioMediaCleanupLifecycleServiceImpl implements TwilioMediaCleanupLifecycleService {

    private final InboundMessageRepository inboundMessageRepository;

    @Override
    @Transactional(readOnly = true)
    public TwilioMediaDeletionPreparationDto prepare(UserId userId, InboundMessageId inboundMessageId) {
        var result = inboundMessageRepository.findByIdAndUserId(inboundMessageId.value(), userId.value());
        if (result.isEmpty() || result.orElseThrow().getTwilioMediaSid() == null) {
            return TwilioMediaDeletionPreparationDto.deleted();
        }
        InboundMessageEntity inbound = result.orElseThrow();
        if (inbound.getMediaUrlCiphertext() != null) {
            throw TwilioMediaCleanupException.transientFailure("TWILIO_MEDIA_STILL_IN_USE", null);
        }
        return new TwilioMediaDeletionPreparationDto(
                false,
                inbound.getTwilioMessageSid(),
                inbound.getTwilioMediaSid()
        );
    }

    @Override
    @Transactional
    public void complete(UserId userId, InboundMessageId inboundMessageId, String mediaSid) {
        inboundMessageRepository.findByIdAndUserId(inboundMessageId.value(), userId.value())
                .ifPresent(inbound -> {
                    if (mediaSid.equals(inbound.getTwilioMediaSid())) {
                        inbound.setTwilioMediaSid(null);
                        inboundMessageRepository.save(inbound);
                    }
                });
    }
}
