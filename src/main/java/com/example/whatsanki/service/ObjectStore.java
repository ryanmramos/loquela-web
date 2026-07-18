package com.example.whatsanki.service;

import com.example.whatsanki.dto.StoredObjectMetadataDto;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

public interface ObjectStore {
    void put(String objectKey, Path source, StoredObjectMetadataDto metadata);

    InputStream open(String objectKey);

    void delete(String objectKey);

    URI createPresignedGetUrl(String objectKey, Duration lifetime);
}
