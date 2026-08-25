package com.example.whatsanki.service.util;

import com.example.whatsanki.dto.DetectedDocumentTypeDto;

import java.nio.file.Path;

/** Detects a supported document type from bytes and validates the declared media type. */
public interface DocumentMediaTypeDetector {
    DetectedDocumentTypeDto detect(Path path, String declaredMediaType);
}
