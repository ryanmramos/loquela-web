package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.dto.SourceSummaryDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.SourceManagementService;
import com.example.whatsanki.service.TwilioCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SourcesTwilioCommandHandler implements TwilioCommandHandler {

    static final int SOURCE_LIST_LIMIT = 10;
    static final String NO_SOURCES_RESPONSE = "You have no sources yet. Send *ADD* <text> to create one.";

    private final SourceManagementService sourceManagementService;

    @Override
    public InboundMessageEntity.CommandType supportedCommand() {
        return InboundMessageEntity.CommandType.SOURCES;
    }

    @Override
    public TwilioWebhookResponseDto handle(TwilioCommandContextDto context) {
        List<SourceSummaryDto> sources = sourceManagementService.listRecent(
                context.userId(), SOURCE_LIST_LIMIT
        );
        log.info("Processed SOURCES command userId={} resultCount={}", context.userId().value(), sources.size());
        if (sources.isEmpty()) {
            return new TwilioWebhookResponseDto(NO_SOURCES_RESPONSE);
        }

        StringBuilder response = new StringBuilder("Your recent sources:\n");
        for (int index = 0; index < sources.size(); index++) {
            SourceSummaryDto source = sources.get(index);
            response.append(index + 1)
                    .append(". ")
                    .append(source.reference())
                    .append(" — ")
                    .append(safeDisplayName(source.displayName()))
                    .append(" — ")
                    .append(status(source));
            if (index < sources.size() - 1) {
                response.append('\n');
            }
        }
        response.append("\nDelete one with *DELETE SOURCE* <id>.");
        return new TwilioWebhookResponseDto(response.toString());
    }

    private String status(SourceSummaryDto source) {
        return switch (source.status()) {
            case RECEIVED, FETCHING, EXTRACTING, INDEXING -> "processing";
            case STORED -> "stored (awaiting extraction)";
            case READY -> readyStatus(source);
            case FAILED -> source.errorCode() == null ? "failed" : "failed (" + source.errorCode() + ")";
            case DELETING -> "deleting";
            case DELETED -> "deleted";
        };
    }

    private String readyStatus(SourceSummaryDto source) {
        if (source.passageCount() == null) {
            return "ready";
        }
        return "ready (" + source.passageCount() + (source.passageCount() == 1 ? " passage)" : " passages)");
    }

    private String safeDisplayName(String displayName) {
        // Future file names are user-controlled. Collapse line breaks and cap their
        // length so one source cannot corrupt or dominate the WhatsApp response.
        String singleLine = displayName.replaceAll("[\\p{Z}\\s]+", " ").strip();
        int codePoints = singleLine.codePointCount(0, singleLine.length());
        if (codePoints <= 80) {
            return singleLine;
        }
        int end = singleLine.offsetByCodePoints(0, 79);
        return singleLine.substring(0, end) + "…";
    }
}
