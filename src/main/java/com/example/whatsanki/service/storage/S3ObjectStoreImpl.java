package com.example.whatsanki.service.storage;

import com.example.whatsanki.dto.StoredObjectMetadataDto;
import com.example.whatsanki.dto.StoredObjectReferenceDto;
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
import java.util.ArrayList;
import java.util.List;

/** Implements private object storage with S3-compatible clients and bounded listing. */
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
            // Object keys contain private ownership identifiers, so operational logs
            // intentionally include only non-identifying storage metadata.
            log.info("Stored private object byteCount={} mediaType={}",
                    metadata.contentLength(), metadata.mediaType());
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
            log.info("Deleted private object");
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

    @Override
    public List<StoredObjectReferenceDto> list(String prefix, int maximum) {
        validateObjectKey(prefix);
        if (maximum <= 0) throw new IllegalArgumentException("maximum must be positive");
        try {
            List<StoredObjectReferenceDto> objects = new ArrayList<>();
            String continuationToken = null;
            do {
                String requestToken = continuationToken;
                var response = s3Client.listObjectsV2(builder -> builder.bucket(bucket).prefix(prefix)
                        .maxKeys(Math.min(1_000, maximum - objects.size()))
                        .continuationToken(requestToken));
                response.contents().forEach(object -> objects.add(
                        new StoredObjectReferenceDto(object.key(), object.lastModified())));
                continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;
            } while (continuationToken != null && objects.size() < maximum);
            return List.copyOf(objects);
        } catch (RuntimeException exception) {
            throw new ObjectStoreException("Could not list objects", exception);
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
