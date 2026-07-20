package com.example.whatsanki.service.twilio;

import java.net.URI;

public interface TwilioMessageClient {
    String createMessage(String recipient, String sender, String body, URI statusCallbackUrl);
}
