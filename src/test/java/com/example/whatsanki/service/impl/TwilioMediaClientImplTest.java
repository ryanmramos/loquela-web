package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.TwilioMediaCleanupException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwilioMediaClientImplTest {

    private static final String MESSAGE_SID = "MM11111111111111111111111111111111";
    private static final String MEDIA_SID = "ME22222222222222222222222222222222";

    @Mock private HttpClient httpClient;
    @Mock private HttpResponse<Void> response;
    private TwilioMediaClientImpl client;

    @BeforeEach
    void setUp() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.getTwilio().setAccountSid("AC33333333333333333333333333333333");
        configuration.getTwilio().setAuthToken("token");
        client = new TwilioMediaClientImpl(configuration, httpClient);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void deletesCanonicalMediaResourceWithAuthentication() throws Exception {
        when(response.statusCode()).thenReturn(204);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        assertThatCode(() -> client.delete(MESSAGE_SID, MEDIA_SID)).doesNotThrowAnyException();

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().method()).isEqualTo("DELETE");
        assertThat(request.getValue().uri().getHost()).isEqualTo("api.twilio.com");
        assertThat(request.getValue().uri().getPath()).endsWith("/" + MEDIA_SID + ".json");
        assertThat(request.getValue().headers().firstValue("Authorization")).isPresent();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void treatsProviderOutageAsTransient() throws Exception {
        when(response.statusCode()).thenReturn(503);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        assertThatThrownBy(() -> client.delete(MESSAGE_SID, MEDIA_SID))
                .isInstanceOfSatisfying(TwilioMediaCleanupException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.TRANSIENT);
                    assertThat(exception.errorCode()).isEqualTo("TWILIO_MEDIA_DELETE_TEMPORARY_FAILURE");
                });
    }
}
