package com.example.whatsanki.dto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/** A bounded temporary download. Callers must close it to remove the local file. */
public record DownloadedMediaDto(Path path, long contentLength, byte[] sha256) implements AutoCloseable {
    public DownloadedMediaDto {
        Objects.requireNonNull(path, "path must not be null");
        if (contentLength < 0) {
            throw new IllegalArgumentException("contentLength must not be negative");
        }
        sha256 = Arrays.copyOf(Objects.requireNonNull(sha256, "sha256 must not be null"), sha256.length);
    }

    @Override
    public byte[] sha256() {
        return Arrays.copyOf(sha256, sha256.length);
    }

    @Override
    public void close() throws IOException {
        Files.deleteIfExists(path);
    }
}
