package com.example.whatsanki.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "whatsanki")
public class WhatsAnkiConfigurationDto {

    @NotNull
    private URI publicBaseUrl = URI.create("http://localhost:8080");

    private String dataEncryptionKey = "";

    @Valid
    @NotNull
    private Twilio twilio = new Twilio();

    @Valid
    @NotNull
    private Ingestion ingestion = new Ingestion();

    @Valid
    @NotNull
    private Jobs jobs = new Jobs();

    @Valid
    @NotNull
    private ObjectStore objectStore = new ObjectStore();

    @Valid
    @NotNull
    private Dictionary dictionary = new Dictionary();

    @Valid
    @NotNull
    private Retention retention = new Retention();

    @Valid
    @NotNull
    private Languages languages = new Languages();

    @Getter
    @Setter
    public static class Twilio {
        private String accountSid = "";
        private String authToken = "";
        private String whatsappFrom = "";
        private URI statusCallbackUrl;
    }

    @Getter
    @Setter
    public static class Ingestion {
        @Positive
        private long maxDownloadBytes = 15L * 1024 * 1024;

        @Positive
        private long maxExtractedCharacters = 2_000_000L;

        @Positive
        private int maxPdfPages = 2_000;

        @Positive
        private int maxEpubEntries = 10_000;

        @NotNull
        private Duration extractionTimeout = Duration.ofMinutes(1);
    }

    @Getter
    @Setter
    public static class Jobs {
        private boolean enabled = true;

        @NotNull
        private Duration pollInterval = Duration.ofSeconds(1);

        @NotNull
        private Duration leaseDuration = Duration.ofMinutes(5);

        @Positive
        private int batchSize = 10;

        @Positive
        private int defaultMaxAttempts = 3;

        @Positive
        private int maxPayloadBytes = 4_096;

        @NotNull
        private Duration retryBaseDelay = Duration.ofSeconds(5);

        @NotNull
        private Duration retryMaxDelay = Duration.ofMinutes(15);
    }

    @Getter
    @Setter
    public static class ObjectStore {
        @NotNull
        private URI endpoint = URI.create("http://localhost:9000");

        @NotBlank
        private String region = "us-east-1";

        @NotBlank
        private String bucket = "whatsanki-local";

        private String accessKey = "";
        private String secretKey = "";
        private boolean pathStyle = true;
    }

    @Getter
    @Setter
    public static class Dictionary {
        @NotBlank
        private String provider = "fake";

        private URI baseUrl;
        private String apiKey = "";

        @NotBlank
        private String userAgent = "WhatsAnki/0.1";
    }

    @Getter
    @Setter
    public static class Retention {
        @NotNull
        private Duration inboundBody = Duration.ofDays(7);

        @NotNull
        private Duration originalDocument = Duration.ofDays(30);

        @NotNull
        private Duration lookupSession = Duration.ofMinutes(30);

        @NotNull
        private Duration exportArtifact = Duration.ofHours(24);
    }

    @Getter
    @Setter
    public static class Languages {
        @NotBlank
        private String defaultSource = "es";

        @NotBlank
        private String defaultDefinition = "en";

        @NotEmpty
        private List<@NotBlank String> supportedPairs = new ArrayList<>(List.of("es:en", "en:es"));
    }
}
