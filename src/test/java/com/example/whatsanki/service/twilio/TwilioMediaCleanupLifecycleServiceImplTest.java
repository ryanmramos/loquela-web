package com.example.whatsanki.service.twilio;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.service.exception.TwilioMediaCleanupException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TwilioMediaCleanupLifecycleServiceImplTest {

    private final InboundMessageRepository repository = mock(InboundMessageRepository.class);
    private final TwilioMediaCleanupLifecycleServiceImpl service =
            new TwilioMediaCleanupLifecycleServiceImpl(repository);

    @Test
    void waitsUntilDownloadNoLongerNeedsStagedUrl() {
        UserId userId = UserId.random();
        InboundMessageId inboundId = InboundMessageId.random();
        InboundMessageEntity inbound = inbound(userId, inboundId);
        inbound.setMediaUrlCiphertext(new byte[]{1});
        when(repository.findByIdAndUserId(inboundId.value(), userId.value()))
                .thenReturn(Optional.of(inbound));

        assertThatThrownBy(() -> service.prepare(userId, inboundId))
                .isInstanceOfSatisfying(TwilioMediaCleanupException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("TWILIO_MEDIA_STILL_IN_USE"));
    }

    @Test
    void clearsMediaSidAfterSuccessfulProviderDeletion() {
        UserId userId = UserId.random();
        InboundMessageId inboundId = InboundMessageId.random();
        InboundMessageEntity inbound = inbound(userId, inboundId);
        when(repository.findByIdAndUserId(inboundId.value(), userId.value()))
                .thenReturn(Optional.of(inbound));

        var prepared = service.prepare(userId, inboundId);
        service.complete(userId, inboundId, prepared.mediaSid());

        assertThat(inbound.getTwilioMediaSid()).isNull();
        verify(repository).save(inbound);
    }

    private InboundMessageEntity inbound(UserId userId, InboundMessageId inboundId) {
        InboundMessageEntity inbound = new InboundMessageEntity();
        inbound.setId(inboundId.value());
        inbound.setUserId(userId.value());
        inbound.setTwilioMessageSid("MM11111111111111111111111111111111");
        inbound.setTwilioMediaSid("ME22222222222222222222222222222222");
        return inbound;
    }
}
