package com.example.whatsanki.service;

import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.SourceDeletionRequestDto;
import com.example.whatsanki.dto.SourceSummaryDto;

import java.util.List;

/** Owns source listing and the transition into asynchronous deletion. */
public interface SourceManagementService {
    List<SourceSummaryDto> listRecent(UserId userId, int limit);

    SourceDeletionRequestDto requestDeletion(UserId userId, String sourceReference);
}
