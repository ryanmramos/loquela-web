package com.example.whatsanki.service.storage;

import com.example.whatsanki.dto.StoredObjectMetadataDto;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import com.example.whatsanki.dto.StoredObjectReferenceDto;

/** Stores private originals and artifacts behind deterministic keys. */
public interface ObjectStore {
    void put(String objectKey, Path source, StoredObjectMetadataDto metadata);

    InputStream open(String objectKey);

    void delete(String objectKey);

    URI createPresignedGetUrl(String objectKey, Duration lifetime);

    /** Lists at most {@code maximum} private objects for bounded orphan cleanup. */
    default List<StoredObjectReferenceDto> list(String prefix, int maximum) { return List.of(); }
}
