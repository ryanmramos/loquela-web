package com.example.whatsanki.service.twilio;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.twilio.TwilioMediaClient;
import com.example.whatsanki.service.exception.TwilioMediaCleanupException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@Slf4j
public class TwilioMediaClientImpl implements TwilioMediaClient {

    private final WhatsAnkiConfigurationDto configuration;
    private final HttpClient httpClient;

    public TwilioMediaClientImpl(
            WhatsAnkiConfigurationDto configuration,
            @Qualifier("twilioMediaHttpClient") HttpClient httpClient
    ) {
        this.configuration = configuration;
        this.httpClient = httpClient;
    }

    @Override
    public void delete(String messageSid, String mediaSid) {
        validateSid(messageSid, "(?:SM|MM)[0-9a-fA-F]{32}");
        validateSid(mediaSid, "ME[0-9a-fA-F]{32}");
        URI uri = URI.create("https://api.twilio.com/2010-04-01/Accounts/"
                + configuration.getTwilio().getAccountSid()
                + "/Messages/" + messageSid + "/Media/" + mediaSid + ".json");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(configuration.getIngestion().getMediaReadTimeout())
                .header("Authorization", basicAuthorization())
                .DELETE()
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status == 204 || status == 404) {
                log.info("Deleted Twilio media resource messageSid={} mediaSid={}", messageSid, mediaSid);
                return;
            }
            if (status == 429 || status >= 500) {
                throw TwilioMediaCleanupException.transientFailure("TWILIO_MEDIA_DELETE_TEMPORARY_FAILURE", null);
            }
            throw TwilioMediaCleanupException.permanent("TWILIO_MEDIA_DELETE_REJECTED");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw TwilioMediaCleanupException.transientFailure("TWILIO_MEDIA_DELETE_INTERRUPTED", exception);
        } catch (IOException exception) {
            throw TwilioMediaCleanupException.transientFailure("TWILIO_MEDIA_DELETE_FAILED", exception);
        }
    }

    private String basicAuthorization() {
        String credentials = configuration.getTwilio().getAccountSid()
                + ":" + configuration.getTwilio().getAuthToken();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private void validateSid(String value, String pattern) {
        if (value == null || !value.matches(pattern)) {
            throw TwilioMediaCleanupException.permanent("INVALID_TWILIO_MEDIA_REFERENCE");
        }
    }
}
