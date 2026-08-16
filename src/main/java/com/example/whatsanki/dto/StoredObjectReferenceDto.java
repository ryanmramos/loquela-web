package com.example.whatsanki.dto;

import java.time.Instant;

public record StoredObjectReferenceDto(String objectKey, Instant lastModified) { }
