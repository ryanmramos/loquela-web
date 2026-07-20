package com.example.whatsanki.service.impl;

import com.example.whatsanki.dto.DownloadedMediaDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.TwilioMediaDownloader;
import com.example.whatsanki.service.exception.MediaAcquisitionException;
import com.example.whatsanki.service.util.TwilioMediaUrlPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
@Slf4j
public class TwilioMediaDownloaderImpl implements TwilioMediaDownloader {

    private final WhatsAnkiConfigurationDto configuration;
    private final TwilioMediaUrlPolicy urlPolicy;
    private final HttpClient httpClient;

    public TwilioMediaDownloaderImpl(
            WhatsAnkiConfigurationDto configuration,
            TwilioMediaUrlPolicy urlPolicy,
            @Qualifier("twilioMediaHttpClient") HttpClient httpClient
    ) {
        this.configuration = configuration;
        this.urlPolicy = urlPolicy;
        this.httpClient = httpClient;
    }

    @Override
    public DownloadedMediaDto download(URI mediaUri) {
        urlPolicy.validate(mediaUri);
        try {
            HttpResponse<InputStream> response = send(mediaUri, true);
            if (!isRedirect(response.statusCode())) {
                return handleResponse(response);
            }

            // Twilio's authenticated API URL redirects to a short-lived CDN URL.
            // Resolve it manually so the Basic Authorization header is never copied
            // to a different authority by the HTTP client.
            URI redirectUri;
            try {
                redirectUri = redirectUri(response);
                urlPolicy.validateRedirect(redirectUri);
            } finally {
                closeQuietly(response.body());
            }

            log.info("Following validated Twilio media redirect redirectHost={}", redirectUri.getHost());
            HttpResponse<InputStream> redirectedResponse = send(redirectUri, false);
            if (isRedirect(redirectedResponse.statusCode())) {
                closeQuietly(redirectedResponse.body());
                throw MediaAcquisitionException.permanent("MEDIA_REDIRECT_REJECTED");
            }
            return handleResponse(redirectedResponse);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw MediaAcquisitionException.transientFailure("MEDIA_DOWNLOAD_INTERRUPTED", exception);
        } catch (IOException exception) {
            throw MediaAcquisitionException.transientFailure("MEDIA_DOWNLOAD_FAILED", exception);
        }
    }

    private HttpResponse<InputStream> send(URI uri, boolean authenticated)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(configuration.getIngestion().getMediaReadTimeout())
                .header("Accept", "application/octet-stream")
                .GET();
        if (authenticated) {
            request.header("Authorization", basicAuthorization());
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    private URI redirectUri(HttpResponse<InputStream> response) {
        String location = response.headers().firstValue("Location")
                .orElseThrow(() -> MediaAcquisitionException.permanent("MEDIA_REDIRECT_REJECTED"));
        try {
            URI redirectUri = URI.create(location);
            if (!redirectUri.isAbsolute()) {
                throw MediaAcquisitionException.permanent("MEDIA_REDIRECT_REJECTED");
            }
            return redirectUri;
        } catch (IllegalArgumentException exception) {
            throw MediaAcquisitionException.permanent("MEDIA_REDIRECT_REJECTED", exception);
        }
    }

    private boolean isRedirect(int status) {
        return status >= 300 && status < 400;
    }

    private DownloadedMediaDto handleResponse(HttpResponse<InputStream> response) {
        int status = response.statusCode();
        if (status == 429 || status >= 500) {
            closeQuietly(response.body());
            throw MediaAcquisitionException.transientFailure("TWILIO_MEDIA_TEMPORARY_FAILURE", null);
        }
        if (isRedirect(status)) {
            closeQuietly(response.body());
            throw MediaAcquisitionException.permanent("MEDIA_REDIRECT_REJECTED");
        }
        if (status < 200 || status >= 300) {
            closeQuietly(response.body());
            throw MediaAcquisitionException.permanent("TWILIO_MEDIA_REJECTED");
        }

        long maximumBytes = configuration.getIngestion().getMaxDownloadBytes();
        long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (declaredLength > maximumBytes) {
            closeQuietly(response.body());
            throw MediaAcquisitionException.permanent("DOCUMENT_TOO_LARGE");
        }

        Path temporaryFile = null;
        try (InputStream input = response.body()) {
            temporaryFile = Files.createTempFile("whatsanki-media-", ".download");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long copied = copyBounded(input, temporaryFile, digest, maximumBytes);
            log.info("Downloaded Twilio media byteCount={}", copied);
            return new DownloadedMediaDto(temporaryFile, copied, digest.digest());
        } catch (MediaAcquisitionException exception) {
            deleteQuietly(temporaryFile);
            throw exception;
        } catch (IOException | NoSuchAlgorithmException exception) {
            deleteQuietly(temporaryFile);
            throw MediaAcquisitionException.transientFailure("MEDIA_DOWNLOAD_FAILED", exception);
        }
    }

    private long copyBounded(InputStream input, Path destination, MessageDigest digest, long maximumBytes)
            throws IOException {
        long total = 0;
        byte[] buffer = new byte[64 * 1024];
        try (var fileOutput = Files.newOutputStream(destination, StandardOpenOption.TRUNCATE_EXISTING);
             var output = new DigestOutputStream(fileOutput, digest)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maximumBytes) {
                    throw MediaAcquisitionException.permanent("DOCUMENT_TOO_LARGE");
                }
                output.write(buffer, 0, read);
            }
        }
        return total;
    }

    private String basicAuthorization() {
        String credentials = configuration.getTwilio().getAccountSid()
                + ":" + configuration.getTwilio().getAuthToken();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private void closeQuietly(InputStream input) {
        if (input == null) {
            return;
        }
        try {
            input.close();
        } catch (IOException ignored) {
            // The response is already being rejected; close failure has no safer action.
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Could not remove failed temporary media download exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
