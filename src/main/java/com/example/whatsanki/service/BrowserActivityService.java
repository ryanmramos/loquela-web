package com.example.whatsanki.service;

import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ActivityItemDto;
import com.example.whatsanki.dto.ActivityPageDto;
import com.example.whatsanki.dto.ActivityStatusQueryResponseDto;

import java.util.List;

/** Builds owner-scoped activity views from source, lookup, and export state. */
public interface BrowserActivityService {
    ActivityPageDto list(
            String googleSubject,
            ActivityItemDto.Category category,
            ActivityItemDto.Lifecycle lifecycle,
            String cursor,
            int limit
    );

    ActivityPageDto listByUser(
            UserId userId,
            ActivityItemDto.Category category,
            ActivityItemDto.Lifecycle lifecycle,
            String cursor,
            int limit
    );

    ActivityStatusQueryResponseDto queryStatuses(String googleSubject, List<String> references);
}
