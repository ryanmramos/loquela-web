package com.example.whatsanki.service.util;

import com.example.whatsanki.dto.DetectedDocumentTypeDto;

import java.nio.file.Path;

public interface DocumentMediaTypeDetector {
    DetectedDocumentTypeDto detect(Path path, String declaredMediaType);
}
