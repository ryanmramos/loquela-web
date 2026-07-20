package com.example.whatsanki.service.twilio;

import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.WhatsAppAddress;
import com.example.whatsanki.dto.OutboundMessageDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.twilio.TwilioMessageClient;
import com.example.whatsanki.service.exception.OutboundDeliveryException;
import com.twilio.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TwilioOutboundMessengerImplTest {
    private final TwilioMessageClient client = mock(TwilioMessageClient.class);
    private WhatsAnkiConfigurationDto configuration;
    private TwilioOutboundMessengerImpl messenger;

    @BeforeEach
    void setUp() {
        configuration = new WhatsAnkiConfigurationDto();
        configuration.getTwilio().setAccountSid("AC0123456789abcdef0123456789abcdef");
        configuration.getTwilio().setAuthToken("token");
        configuration.getTwilio().setWhatsappFrom("whatsapp:+14155238886");
        configuration.getTwilio().setStatusCallbackUrl(
                URI.create("https://example.test/webhooks/twilio/message-status")
        );
        messenger = new TwilioOutboundMessengerImpl(client, configuration);
    }

    @Test
    void sendsThroughInjectedClientWithoutLoggingMessageContent() {
        WhatsAppAddress recipient = new WhatsAppAddress("whatsapp:+15551234567");
        OutboundMessageDto message = new OutboundMessageDto("private result");
        when(client.createMessage(
                recipient.value(), configuration.getTwilio().getWhatsappFrom(), message.body(),
                configuration.getTwilio().getStatusCallbackUrl()
        )).thenReturn("SM0123456789abcdef0123456789abcdef");

        var result = messenger.send(recipient, message);

        assertThat(result.value()).isEqualTo("SM0123456789abcdef0123456789abcdef");
        verify(client).createMessage(
                recipient.value(), configuration.getTwilio().getWhatsappFrom(), message.body(),
                configuration.getTwilio().getStatusCallbackUrl()
        );
    }

    @Test
    void rejectsMissingCredentialsAsPermanentConfigurationFailure() {
        configuration.getTwilio().setAuthToken("");

        assertThatThrownBy(() -> messenger.send(
                new WhatsAppAddress("whatsapp:+15551234567"), new OutboundMessageDto("result")
        )).isInstanceOfSatisfying(OutboundDeliveryException.class, exception -> {
            assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.PERMANENT);
            assertThat(exception.errorCode()).isEqualTo("OUTBOUND_CONFIGURATION_INVALID");
        });
    }

    @Test
    void classifiesTwilioRateLimitAsTransient() {
        WhatsAppAddress recipient = new WhatsAppAddress("whatsapp:+15551234567");
        OutboundMessageDto message = new OutboundMessageDto("result");
        when(client.createMessage(
                recipient.value(), configuration.getTwilio().getWhatsappFrom(), message.body(),
                configuration.getTwilio().getStatusCallbackUrl()
        )).thenThrow(new ApiException(
                "rate limited", 20429, "", 429, 429, null, false, null
        ));

        assertThatThrownBy(() -> messenger.send(recipient, message))
                .isInstanceOfSatisfying(OutboundDeliveryException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.TRANSIENT);
                    assertThat(exception.errorCode()).isEqualTo("TWILIO_TEMPORARY_FAILURE");
                });
    }
}
