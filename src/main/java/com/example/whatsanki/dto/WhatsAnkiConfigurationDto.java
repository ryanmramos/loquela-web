package com.example.whatsanki.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
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
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private Security security = new Security();

    @Valid
    @NotNull
    private Ingestion ingestion = new Ingestion();

    @Valid
    @NotNull
    private Jobs jobs = new Jobs();

    @Valid
    @NotNull
    private Search search = new Search();

    @Valid
    @NotNull
    private Outbound outbound = new Outbound();

    @Valid
    @NotNull
    private ObjectStore objectStore = new ObjectStore();

    @Valid
    @NotNull
    private Dictionary dictionary = new Dictionary();

    @Valid
    @NotNull
    private Exporter exporter = new Exporter();

    @Valid
    @NotNull
    private Retention retention = new Retention();

    @Valid
    @NotNull
    private AccountDeletion accountDeletion = new AccountDeletion();

    @Valid
    @NotNull
    private Youtube youtube = new Youtube();

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

        @NotEmpty
        private List<@NotBlank String> mediaAllowedHosts = new ArrayList<>(List.of("api.twilio.com"));

        @NotEmpty
        private List<@NotBlank String> mediaRedirectAllowedHosts =
                new ArrayList<>(List.of("mms.twiliocdn.com"));
    }

    @Getter
    @Setter
    public static class Security {
        private boolean googleOauthEnabled;
        private boolean localLoginEnabled;
        private boolean secureCookies;
        @NotNull
        private URI frontendBaseUrl = URI.create("http://localhost:4200");

        @NotNull
        private Duration sessionTimeout = Duration.ofHours(8);

        private List<@NotBlank String> allowedOrigins = new ArrayList<>();

        private List<@NotBlank String> bootstrapAdminEmails = new ArrayList<>();

        @Positive
        private int authRequestsPerMinute = 30;

        @Positive
        private int linkRequestsPerMinute = 20;

        @AssertTrue(message = "whatsanki.security.session-timeout must be positive")
        public boolean isSessionTimeoutPositive() {
            return sessionTimeout != null && !sessionTimeout.isZero() && !sessionTimeout.isNegative();
        }
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

        @Positive
        private int maxPassageCharacters = 1_200;

        @Positive
        private int maxPassageTokens = 250;

        @Positive
        private int maxPassageSentences = 3;

        @NotNull
        private Duration extractionTimeout = Duration.ofMinutes(1);

        @NotNull
        private Duration mediaConnectTimeout = Duration.ofSeconds(10);

        @NotNull
        private Duration mediaReadTimeout = Duration.ofSeconds(30);

        @Positive
        private int extractionThreads = 2;

        @NotNull
        private Duration uploadIntentLifetime = Duration.ofMinutes(15);

        @AssertTrue(message = "whatsanki.ingestion.extraction-timeout must be positive")
        public boolean isExtractionTimeoutPositive() {
            return extractionTimeout != null && !extractionTimeout.isZero() && !extractionTimeout.isNegative();
        }

        @AssertTrue(message = "whatsanki.ingestion media timeouts must be positive")
        public boolean areMediaTimeoutsPositive() {
            return isPositive(mediaConnectTimeout)
                    && isPositive(mediaReadTimeout)
                    && isPositive(uploadIntentLifetime);
        }

        private boolean isPositive(Duration duration) {
            return duration != null && !duration.isZero() && !duration.isNegative();
        }
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
    public static class Search {
        @Positive
        private int maxTermCharacters = 256;

        @Positive
        private int maxResults = 20;

        @Positive
        private int candidateMultiplier = 5;

        @Positive
        private int maxCandidates = 100;

        @Positive
        private int idealPassageCharacters = 320;

        @Positive
        private int lookupResults = 5;

        @Positive
        private int lookupMaxResultsPerSource = 2;

        @Positive
        private int maxDictionarySearchTerms = 32;

        @AssertTrue(message = "whatsanki.search lookup result limits must be internally consistent")
        public boolean areLookupLimitsValid() {
            return lookupResults <= maxResults
                    && lookupMaxResultsPerSource <= lookupResults;
        }
    }

    @Getter
    @Setter
    public static class Outbound {
        /** Leaves room below Twilio's body limit for transport-specific handling. */
        @Min(200)
        private int maxMessageCharacters = 1_500;

        @Positive
        private int maxLookupParts = 10;
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

        @NotNull
        private Duration presignedUrlLifetime = Duration.ofMinutes(15);
    }

    @Getter
    @Setter
    public static class Dictionary {
        @NotBlank
        private String provider = "fake";

        /**
         * Ordered default providers. When empty, {@link #provider} remains the
         * backwards-compatible single-provider selection. Entries after the first are
         * consulted only when the earlier provider has no definition for a supported
         * pair; provider outages never silently change dictionary authority.
         */
        private List<@NotBlank String> providerOrder = new ArrayList<>();

        /**
         * Optional pair-specific provider preference, keyed as {@code source:definition}.
         * This lets a language-specific provider precede a broad fallback such as
         * Wiktionary without coupling lookup/card code to either implementation.
         */
        private Map<@NotBlank String, List<@NotBlank String>> routes = new LinkedHashMap<>();

        /** Wikimedia Action API endpoint template for the generic prototype adapter. */
        @NotBlank
        private String wiktionaryBaseUrlTemplate = "https://{definitionLanguage}.wiktionary.org/w/api.php";

        @NotEmpty
        private List<@NotBlank String> wiktionaryAllowedHosts = new ArrayList<>(
                List.of("en.wiktionary.org", "es.wiktionary.org")
        );

        /** Fixed origin for the unofficial RAE API Spanish dictionary adapter. */
        @NotNull
        private URI raeBaseUrl = URI.create("https://rae-api.com");

        @NotEmpty
        private List<@NotBlank String> raeAllowedHosts = new ArrayList<>(List.of("rae-api.com"));

        /**
         * Optional for the API's free tier, but configured server-side to unlock the
         * quota associated with a developer key. It must never enter logs or URLs.
         */
        private String raeApiKey = "";

        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(3);

        @NotNull
        private Duration readTimeout = Duration.ofSeconds(8);

        @Positive
        private long maxResponseBytes = 1_048_576L;

        @Positive
        private int maxConcurrentRequests = 4;

        /** Kept for a future commercial adapter. */
        private URI baseUrl;
        private String apiKey = "";

        @NotBlank
        private String userAgent = "WhatsAnki/0.1";

        @AssertTrue(message = "whatsanki.dictionary timeouts must be positive")
        public boolean areTimeoutsPositive() {
            return positive(connectTimeout) && positive(readTimeout);
        }

        private boolean positive(Duration duration) {
            return duration != null && !duration.isZero() && !duration.isNegative();
        }
    }

    @Getter
    @Setter
    public static class Exporter {
        @NotBlank
        private String pythonExecutable = "python3";

        @NotBlank
        private String module = "whatsanki_exporter";

        @NotNull
        private Path workingDirectory = Path.of("exporter");

        @NotNull
        private Duration timeout = Duration.ofMinutes(2);

        @Positive
        private long maxArtifactBytes = 100L * 1024 * 1024;

        @AssertTrue(message = "whatsanki.exporter.timeout must be positive")
        public boolean isTimeoutPositive() {
            return timeout != null && !timeout.isZero() && !timeout.isNegative();
        }
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

        @NotNull
        private Duration scanInterval = Duration.ofHours(1);

        @Positive
        private int scanBatchSize = 100;

        @AssertTrue(message = "whatsanki.retention durations must be positive")
        public boolean areDurationsPositive() {
            return isPositive(inboundBody)
                    && isPositive(originalDocument)
                    && isPositive(lookupSession)
                    && isPositive(exportArtifact)
                    && isPositive(scanInterval);
        }

        private boolean isPositive(Duration duration) {
            return duration != null && !duration.isZero() && !duration.isNegative();
        }
    }

    @Getter
    @Setter
    public static class AccountDeletion {
        @NotNull
        private Duration confirmationLifetime = Duration.ofMinutes(10);

        @AssertTrue(message = "whatsanki.account-deletion.confirmation-lifetime must be positive")
        public boolean isConfirmationLifetimePositive() {
            return confirmationLifetime != null
                    && !confirmationLifetime.isZero()
                    && !confirmationLifetime.isNegative();
        }
    }

    @Getter
    @Setter
    public static class Youtube {
        @NotBlank private String executable = "yt-dlp";
        private String expectedVersion = "";
        private boolean automaticCaptionFallback = true;
        @NotNull private Duration processTimeout = Duration.ofMinutes(1);
        @Positive private int maxMetadataBytes = 1_048_576;
        @Positive private int maxSubtitleBytes = 5_242_880;
        @Positive private int maxCapturedErrorBytes = 16_384;
        @Positive private int maxCaptionCues = 50_000;
        @NotNull private Duration playbackLead = Duration.ofSeconds(3);

        @AssertTrue(message = "whatsanki.youtube durations must be non-negative and timeout must be positive")
        public boolean areDurationsValid() {
            return processTimeout != null && !processTimeout.isZero() && !processTimeout.isNegative()
                    && playbackLead != null && !playbackLead.isNegative();
        }
    }

    @Getter
    @Setter
    public static class Languages {
        @NotBlank
        private String defaultSource = "es";

        @NotBlank
        private String defaultDefinition = "en";

        @NotEmpty
        private List<@NotBlank String> supportedPairs =
                new ArrayList<>(List.of("es:en", "en:es", "es:es", "en:en"));
    }
}
