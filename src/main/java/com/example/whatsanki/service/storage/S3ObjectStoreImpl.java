package com.example.whatsanki.service.storage;

import com.example.whatsanki.dto.StoredObjectMetadataDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.ObjectStoreException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

@Service
@Slf4j
public class S3ObjectStoreImpl implements ObjectStore {

    private final String bucket;
    private final S3Client s3Client;
    private final S3Presigner presigner;

    public S3ObjectStoreImpl(WhatsAnkiConfigurationDto configuration) {
        var objectStore = configuration.getObjectStore();
        bucket = objectStore.getBucket();
        AwsCredentialsProvider credentials = credentialsProvider(objectStore);
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(objectStore.isPathStyle())
                .build();

        s3Client = S3Client.builder()
                .endpointOverride(objectStore.getEndpoint())
                .region(Region.of(objectStore.getRegion()))
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Configuration)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        presigner = S3Presigner.builder()
                .endpointOverride(objectStore.getEndpoint())
                .region(Region.of(objectStore.getRegion()))
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Configuration)
                .build();
    }

    @Override
    public void put(String objectKey, Path source, StoredObjectMetadataDto metadata) {
        validateObjectKey(objectKey);
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentLength(metadata.contentLength())
                    .contentType(metadata.mediaType())
                    .metadata(Map.of("sha256", metadata.sha256Hex()))
                    .build();
            s3Client.putObject(request, RequestBody.fromFile(source));
            log.info("Stored object objectKey={} byteCount={} mediaType={}",
                    objectKey, metadata.contentLength(), metadata.mediaType());
        } catch (RuntimeException exception) {
            throw new ObjectStoreException("Could not store object", exception);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        validateObjectKey(objectKey);
        try {
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(objectKey).build()
            );
            return response;
        } catch (RuntimeException exception) {
            throw new ObjectStoreException("Could not open object", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        validateObjectKey(objectKey);
        try {
            // S3 DeleteObject is idempotent: deleting a missing key is successful. This
            // property lets background retries resume source/account deletion safely.
            s3Client.deleteObject(builder -> builder.bucket(bucket).key(objectKey));
            log.info("Deleted object objectKey={}", objectKey);
        } catch (RuntimeException exception) {
            throw new ObjectStoreException("Could not delete object", exception);
        }
    }

    @Override
    public URI createPresignedGetUrl(String objectKey, Duration lifetime) {
        validateObjectKey(objectKey);
        if (lifetime == null || lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("lifetime must be positive");
        }
        try {
            GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(objectKey).build();
            GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                    .signatureDuration(lifetime)
                    .getObjectRequest(get)
                    .build();
            return presigner.presignGetObject(request).url().toURI();
        } catch (Exception exception) {
            throw new ObjectStoreException("Could not create presigned URL", exception);
        }
    }

    @PreDestroy
    void close() {
        presigner.close();
        s3Client.close();
    }

    private AwsCredentialsProvider credentialsProvider(WhatsAnkiConfigurationDto.ObjectStore configuration) {
        boolean hasAccessKey = configuration.getAccessKey() != null && !configuration.getAccessKey().isBlank();
        boolean hasSecretKey = configuration.getSecretKey() != null && !configuration.getSecretKey().isBlank();
        if (hasAccessKey != hasSecretKey) {
            throw new IllegalStateException("Object-store access and secret keys must be configured together");
        }
        if (hasAccessKey) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    configuration.getAccessKey(), configuration.getSecretKey()
            ));
        }
        return DefaultCredentialsProvider.builder().build();
    }

    private void validateObjectKey(String objectKey) {
        if (objectKey == null
                || objectKey.isBlank()
                || objectKey.startsWith("/")
                || objectKey.contains("..")
                || objectKey.contains("\\")) {
            throw new IllegalArgumentException("objectKey is not a safe relative key");
        }
    }
}
