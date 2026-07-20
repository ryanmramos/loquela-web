package com.example.whatsanki.service.twilio;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class TwilioSignatureServiceImplTest {

    private static final String AUTH_TOKEN = "test-auth-token";
    private static final String PUBLIC_URL = "https://whatsanki.example";

    private TwilioSignatureServiceImpl service;

    @BeforeEach
    void setUp() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.setPublicBaseUrl(java.net.URI.create(PUBLIC_URL));
        configuration.getTwilio().setAuthToken(AUTH_TOKEN);
        service = new TwilioSignatureServiceImpl(configuration);
    }

    @Test
    void acceptsSignatureForExactPublicUrlAndFormParameters() throws Exception {
        MockHttpServletRequest request = helpRequest();
        String signature = signature(
                PUBLIC_URL + "/webhooks/twilio/whatsapp",
                Map.of(
                        "Body", "HELP",
                        "From", "whatsapp:+15550000001",
                        "MessageSid", "SM123",
                        "To", "whatsapp:+15550000002"
                )
        );

        assertThat(service.isValid(request, signature)).isTrue();
    }

    @Test
    void rejectsInvalidOrMissingSignature() {
        MockHttpServletRequest request = helpRequest();

        assertThat(service.isValid(request, "invalid")).isFalse();
        assertThat(service.isValid(request, null)).isFalse();
    }

    @Test
    void rejectsAllRequestsWhenAuthTokenIsMissing() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.setPublicBaseUrl(java.net.URI.create(PUBLIC_URL));
        service = new TwilioSignatureServiceImpl(configuration);

        assertThat(service.isValid(helpRequest(), "anything")).isFalse();
    }

    private MockHttpServletRequest helpRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhooks/twilio/whatsapp");
        request.addParameter("MessageSid", "SM123");
        request.addParameter("From", "whatsapp:+15550000001");
        request.addParameter("To", "whatsapp:+15550000002");
        request.addParameter("Body", "HELP");
        return request;
    }

    private String signature(String url, Map<String, String> parameters) throws Exception {
        StringBuilder signedValue = new StringBuilder(url);
        new TreeMap<>(parameters).forEach((name, value) -> signedValue.append(name).append(value));

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(AUTH_TOKEN.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(
                mac.doFinal(signedValue.toString().getBytes(StandardCharsets.UTF_8))
        );
    }
}
