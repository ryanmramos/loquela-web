package com.example.whatsanki.dto;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

public record StoredObjectMetadataDto(long contentLength, byte[] sha256, String mediaType) {
    public StoredObjectMetadataDto {
        if (contentLength < 0) {
            throw new IllegalArgumentException("contentLength must not be negative");
        }
        sha256 = Arrays.copyOf(Objects.requireNonNull(sha256, "sha256 must not be null"), sha256.length);
        Objects.requireNonNull(mediaType, "mediaType must not be null");
    }

    @Override
    public byte[] sha256() {
        return Arrays.copyOf(sha256, sha256.length);
    }

    public String sha256Hex() {
        return HexFormat.of().formatHex(sha256);
    }
}
