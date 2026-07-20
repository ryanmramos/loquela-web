package com.example.whatsanki.service.twilio;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.MediaAcquisitionException;
import com.example.whatsanki.service.util.TwilioMediaUrlPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwilioMediaDownloaderImplTest {

    private static final URI MEDIA_URI = URI.create("https://api.twilio.com/media");

    @Mock private TwilioMediaUrlPolicy urlPolicy;
    @Mock private HttpClient httpClient;

    private WhatsAnkiConfigurationDto configuration;
    private TwilioMediaDownloaderImpl downloader;

    @BeforeEach
    void setUp() {
        configuration = new WhatsAnkiConfigurationDto();
        configuration.getTwilio().setAccountSid("AC123");
        configuration.getTwilio().setAuthToken("token");
        downloader = new TwilioMediaDownloaderImpl(configuration, urlPolicy, httpClient);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void authenticatesStreamsHashesAndDeletesTemporaryFileWhenClosed() throws Exception {
        byte[] content = "hello media".getBytes(StandardCharsets.UTF_8);
        HttpResponse<java.io.InputStream> response = response(200, content, (long) content.length);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        var downloaded = downloader.download(MEDIA_URI);

        assertThat(Files.readAllBytes(downloaded.path())).isEqualTo(content);
        assertThat(downloaded.contentLength()).isEqualTo(content.length);
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().headers().firstValue("Authorization"))
                .contains("Basic QUMxMjM6dG9rZW4=");
        var path = downloaded.path();
        downloaded.close();
        assertThat(path).doesNotExist();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void rejectsOversizedContentBeforeWriting() throws Exception {
        configuration.getIngestion().setMaxDownloadBytes(3);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(200, new byte[]{1, 2, 3, 4}, 4L));

        assertThatThrownBy(() -> downloader.download(MEDIA_URI))
                .isInstanceOfSatisfying(MediaAcquisitionException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("DOCUMENT_TOO_LARGE"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void classifiesRateLimitsAsTransient() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(429, new byte[0], 0L));
        assertThatThrownBy(() -> downloader.download(MEDIA_URI))
                .isInstanceOfSatisfying(MediaAcquisitionException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo("TWILIO_MEDIA_TEMPORARY_FAILURE");
                    assertThat(exception.failureKind().name()).isEqualTo("TRANSIENT");
                });
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void followsOneValidatedRedirectWithoutForwardingAuthorization() throws Exception {
        URI redirectUri = URI.create("https://mms.twiliocdn.com/media.pdf?signature=secret");
        byte[] content = "%PDF-test".getBytes(StandardCharsets.UTF_8);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(
                        response(302, new byte[0], 0L, MEDIA_URI, Map.of("Location", redirectUri.toString())),
                        response(200, content, (long) content.length, redirectUri, Map.of())
                );

        try (var downloaded = downloader.download(MEDIA_URI)) {
            assertThat(Files.readAllBytes(downloaded.path())).isEqualTo(content);
        }

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(requests.getAllValues()).hasSize(2);
        assertThat(requests.getAllValues().get(0).uri()).isEqualTo(MEDIA_URI);
        assertThat(requests.getAllValues().get(0).headers().firstValue("Authorization")).isPresent();
        assertThat(requests.getAllValues().get(1).uri()).isEqualTo(redirectUri);
        assertThat(requests.getAllValues().get(1).headers().firstValue("Authorization")).isEmpty();
        verify(urlPolicy).validateRedirect(redirectUri);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void rejectsRedirectWithoutLocation() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(302, new byte[0], 0L));

        assertThatThrownBy(() -> downloader.download(MEDIA_URI))
                .isInstanceOfSatisfying(MediaAcquisitionException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("MEDIA_REDIRECT_REJECTED"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void rejectsASecondRedirect() throws Exception {
        URI redirectUri = URI.create("https://mms.twiliocdn.com/media.pdf?signature=secret");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(
                        response(302, new byte[0], 0L, MEDIA_URI, Map.of("Location", redirectUri.toString())),
                        response(307, new byte[0], 0L, redirectUri,
                                Map.of("Location", "https://mms.twiliocdn.com/another"))
                );

        assertThatThrownBy(() -> downloader.download(MEDIA_URI))
                .isInstanceOfSatisfying(MediaAcquisitionException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("MEDIA_REDIRECT_REJECTED"));
    }

    private HttpResponse<java.io.InputStream> response(int status, byte[] body, Long contentLength) {
        return response(status, body, contentLength, MEDIA_URI, Map.of());
    }

    private HttpResponse<java.io.InputStream> response(
            int status,
            byte[] body,
            Long contentLength,
            URI responseUri,
            Map<String, String> additionalHeaders
    ) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return status; }
            @Override public HttpRequest request() { return null; }
            @Override public Optional<HttpResponse<java.io.InputStream>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() {
                Map<String, List<String>> values = new java.util.LinkedHashMap<>();
                if (contentLength != null) {
                    values.put("Content-Length", List.of(contentLength.toString()));
                }
                additionalHeaders.forEach((name, value) -> values.put(name, List.of(value)));
                return HttpHeaders.of(values, (name, value) -> true);
            }
            @Override public java.io.InputStream body() { return new ByteArrayInputStream(body); }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return responseUri; }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
