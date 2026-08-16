package com.example.whatsanki.service;

import com.example.whatsanki.dto.AcceptedSourceDto;
import com.example.whatsanki.dto.BrowserSourceDetailsDto;
import com.example.whatsanki.dto.BrowserSourceSummaryDto;
import com.example.whatsanki.dto.PastedTextSourceDto;
import com.example.whatsanki.dto.SourceListDto;
import com.example.whatsanki.dto.SourceMutationDto;

public interface BrowserSourceService {
    PastedTextSourceDto createPastedText(String googleSubject, String text);

    /** @deprecated Direct YouTube acquisition is retired; retained for legacy internal compatibility only. */
    @Deprecated(forRemoval = true)
    AcceptedSourceDto createYouTube(String googleSubject, String url);

    SourceListDto list(
            String googleSubject,
            int page,
            int pageSize,
            String search,
            SourceKind kind,
            SourceStatus status,
            SortField sort,
            SortDirection direction
    );

    BrowserSourceDetailsDto get(String googleSubject, String sourceReference);

    BrowserSourceSummaryDto rename(String googleSubject, String sourceReference, String displayName, long version);

    SourceMutationDto retry(String googleSubject, String sourceReference);

    SourceMutationDto delete(String googleSubject, String sourceReference);

    enum SortField { CREATED_AT, UPDATED_AT, DISPLAY_NAME }

    enum SortDirection { ASC, DESC }

    enum SourceKind { PASTED_TEXT, TXT, PDF, EPUB, YOUTUBE_VIDEO, TIMED_TRANSCRIPT }

    enum SourceStatus {
        RECEIVED, FETCHING, STORED, PARSING, NORMALIZING, EXTRACTING, INDEXING, READY, FAILED, DELETING, DELETED
    }
}
