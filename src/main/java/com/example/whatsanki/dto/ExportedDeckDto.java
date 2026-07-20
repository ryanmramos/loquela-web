package com.example.whatsanki.dto;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/** Verified artifact returned by the isolated Python exporter process. */
public record ExportedDeckDto(Path path, long byteSize, byte[] sha256) {
    public ExportedDeckDto {
        Objects.requireNonNull(path, "path must not be null");
        if (byteSize < 1) {
            throw new IllegalArgumentException("byteSize must be positive");
        }
        sha256 = Arrays.copyOf(Objects.requireNonNull(sha256, "sha256 must not be null"), sha256.length);
        if (sha256.length != 32) {
            throw new IllegalArgumentException("sha256 must contain 32 bytes");
        }
    }

    @Override
    public byte[] sha256() {
        return Arrays.copyOf(sha256, sha256.length);
    }
}
