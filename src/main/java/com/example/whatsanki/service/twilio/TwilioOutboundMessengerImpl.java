package com.example.whatsanki.service.twilio;

import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.ProviderMessageId;
import com.example.whatsanki.domain.WhatsAppAddress;
import com.example.whatsanki.dto.OutboundMessageDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.OutboundDeliveryException;
import com.twilio.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioOutboundMessengerImpl implements OutboundMessenger {
    private static final Pattern ACCOUNT_SID = Pattern.compile("AC[0-9a-fA-F]{32}");

    private final TwilioMessageClient twilioMessageClient;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    public ProviderMessageId send(WhatsAppAddress recipient, OutboundMessageDto message) {
        validateConfiguration();
        try {
            String sid = twilioMessageClient.createMessage(
                    recipient.value(),
                    configuration.getTwilio().getWhatsappFrom(),
                    message.body(),
                    configuration.getTwilio().getStatusCallbackUrl()
            );
            ProviderMessageId providerMessageId = new ProviderMessageId(sid);
            log.info("Twilio accepted outbound WhatsApp message bodyCharacters={}",
                    message.body().codePointCount(0, message.body().length()));
            return providerMessageId;
        } catch (ApiException exception) {
            int status = exception.getHttpStatusCode() == null ? 0 : exception.getHttpStatusCode();
            BackgroundJobFailureKind kind = status == 429 || status >= 500 || status == 0
                    ? BackgroundJobFailureKind.TRANSIENT
                    : BackgroundJobFailureKind.PERMANENT;
            String code = kind == BackgroundJobFailureKind.TRANSIENT
                    ? "TWILIO_TEMPORARY_FAILURE"
                    : "TWILIO_REQUEST_REJECTED";
            throw new OutboundDeliveryException(kind, code, exception);
        } catch (OutboundDeliveryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw OutboundDeliveryException.transientFailure(
                    "TWILIO_TRANSPORT_FAILURE", exception
            );
        }
    }

    private void validateConfiguration() {
        String accountSid = configuration.getTwilio().getAccountSid();
        String authToken = configuration.getTwilio().getAuthToken();
        String sender = configuration.getTwilio().getWhatsappFrom();
        URI callback = configuration.getTwilio().getStatusCallbackUrl();
        try {
            if (accountSid == null || !ACCOUNT_SID.matcher(accountSid).matches()
                    || authToken == null || authToken.isBlank()
                    || sender == null || sender.isBlank()) {
                throw OutboundDeliveryException.permanent("OUTBOUND_CONFIGURATION_INVALID");
            }
            new WhatsAppAddress(sender);
            if (callback != null && !"https".equalsIgnoreCase(callback.getScheme())) {
                throw OutboundDeliveryException.permanent("OUTBOUND_CONFIGURATION_INVALID");
            }
        } catch (IllegalArgumentException exception) {
            throw new OutboundDeliveryException(
                    BackgroundJobFailureKind.PERMANENT,
                    "OUTBOUND_CONFIGURATION_INVALID",
                    exception
            );
        }
    }
}
