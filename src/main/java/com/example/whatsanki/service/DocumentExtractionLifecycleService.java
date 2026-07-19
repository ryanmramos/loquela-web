package com.example.whatsanki.service;

import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DocumentExtractionPreparationDto;
import com.example.whatsanki.dto.PassageDraftDto;

import java.util.List;

public interface DocumentExtractionLifecycleService {
    DocumentExtractionPreparationDto prepare(UserId userId, SourceId sourceId);
    void complete(UserId userId, SourceId sourceId, List<PassageDraftDto> passages, long characterCount);
    void markFailed(UserId userId, SourceId sourceId, String errorCode);
}
