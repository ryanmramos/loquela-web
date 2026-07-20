package com.example.whatsanki.service.twilio;

import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.TwilioMediaDeletionPreparationDto;
import com.example.whatsanki.service.twilio.TwilioMediaCleanupLifecycleService;
import com.example.whatsanki.service.twilio.TwilioMediaCleanupService;
import com.example.whatsanki.service.twilio.TwilioMediaClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioMediaCleanupServiceImpl implements TwilioMediaCleanupService {

    private final TwilioMediaCleanupLifecycleService lifecycleService;
    private final TwilioMediaClient twilioMediaClient;

    @Override
    public void delete(UserId userId, InboundMessageId inboundMessageId) {
        TwilioMediaDeletionPreparationDto preparation = lifecycleService.prepare(userId, inboundMessageId);
        if (preparation.alreadyDeleted()) {
            log.info("Skipped already-deleted Twilio media userId={} inboundMessageId={}",
                    userId.value(), inboundMessageId.value());
            return;
        }
        twilioMediaClient.delete(preparation.messageSid(), preparation.mediaSid());
        lifecycleService.complete(userId, inboundMessageId, preparation.mediaSid());
    }
}
