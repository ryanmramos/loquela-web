package com.example.whatsanki.service.storage;

import com.example.whatsanki.dto.StoredObjectMetadataDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.ObjectStoreException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class S3ObjectStoreIntegrationTest {

    private static final String ACCESS_KEY = "whatsanki-test";
    private static final String SECRET_KEY = "whatsanki-test-password";
    private static final String BUCKET = "whatsanki-test";

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z")
    )
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .withExposedPorts(9000);

    @TempDir Path temporaryDirectory;

    @BeforeAll
    static void createBucket() {
        try (S3Client client = client(endpoint())) {
            client.createBucket(builder -> builder.bucket(BUCKET));
        }
    }

    @Test
    void streamsObjectReadsItPresignsItAndDeletesIdempotently() throws Exception {
        WhatsAnkiConfigurationDto configuration = configuration();
        S3ObjectStoreImpl objectStore = new S3ObjectStoreImpl(configuration);
        Path file = temporaryDirectory.resolve("original");
        byte[] content = "El árbol.".getBytes(StandardCharsets.UTF_8);
        Files.write(file, content);
        String key = "users/00000000-0000-0000-0000-000000000001/sources/"
                + "00000000-0000-0000-0000-000000000002/original";

        try {
            objectStore.put(key, file, new StoredObjectMetadataDto(
                    content.length, MessageDigest.getInstance("SHA-256").digest(content), "text/plain"
            ));
            try (var input = objectStore.open(key)) {
                assertThat(input.readAllBytes()).isEqualTo(content);
            }
            URI presigned = objectStore.createPresignedGetUrl(key, Duration.ofMinutes(5));
            assertThat(presigned.getQuery()).contains("X-Amz-Signature");

            objectStore.delete(key);
            objectStore.delete(key);
            assertThatThrownBy(() -> objectStore.open(key)).isInstanceOf(ObjectStoreException.class);
        } finally {
            objectStore.close();
        }
    }

    private static WhatsAnkiConfigurationDto configuration() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.getObjectStore().setEndpoint(endpoint());
        configuration.getObjectStore().setBucket(BUCKET);
        configuration.getObjectStore().setAccessKey(ACCESS_KEY);
        configuration.getObjectStore().setSecretKey(SECRET_KEY);
        configuration.getObjectStore().setPathStyle(true);
        return configuration;
    }

    private static S3Client client(URI endpoint) {
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)
                ))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    private static URI endpoint() {
        return URI.create("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
    }
}
