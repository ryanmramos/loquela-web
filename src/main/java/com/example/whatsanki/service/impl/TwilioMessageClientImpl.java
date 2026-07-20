package com.example.whatsanki.service.impl;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.TwilioMessageClient;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;

@Service
@RequiredArgsConstructor
public class TwilioMessageClientImpl implements TwilioMessageClient {
    private final WhatsAnkiConfigurationDto configuration;
    private volatile TwilioRestClient client;

    @Override
    public String createMessage(
            String recipient,
            String sender,
            String body,
            URI statusCallbackUrl
    ) {
        MessageCreator creator = Message.creator(
                new PhoneNumber(recipient),
                new PhoneNumber(sender),
                body
        );
        if (statusCallbackUrl != null) {
            creator.setStatusCallback(statusCallbackUrl);
        }
        return creator.create(client()).getSid();
    }

    private TwilioRestClient client() {
        TwilioRestClient current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                // Construct one thread-safe SDK client for the application process. This
                // avoids static Twilio.init state and keeps credentials injectable in tests.
                client = new TwilioRestClient.Builder(
                        configuration.getTwilio().getAccountSid(),
                        configuration.getTwilio().getAuthToken()
                ).build();
            }
            return client;
        }
    }
}
