package com.example.whatsanki.service.document;

import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.dto.ExtractedDocumentDto;

public interface DocumentTextExtractor {
    SourceDocumentEntity.Kind supportedKind();
    ExtractedDocumentDto extract(byte[] content);
}
