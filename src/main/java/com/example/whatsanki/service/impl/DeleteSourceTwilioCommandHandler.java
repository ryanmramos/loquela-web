package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.SourceDeletionRequestDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.SourceManagementService;
import com.example.whatsanki.service.TwilioCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteSourceTwilioCommandHandler implements TwilioCommandHandler {

    private final SourceManagementService sourceManagementService;

    @Override
    public InboundMessageEntity.CommandType supportedCommand() {
        return InboundMessageEntity.CommandType.DELETE_SOURCE;
    }

    @Override
    public TwilioWebhookResponseDto handle(TwilioCommandContextDto context) {
        SourceDeletionRequestDto result = sourceManagementService.requestDeletion(
                context.userId(), context.command().argument()
        );
        log.info(
                "Processed DELETE SOURCE command userId={} outcome={}",
                context.userId().value(), result.outcome()
        );
        return switch (result.outcome()) {
            case QUEUED -> new TwilioWebhookResponseDto(
                    "Source deletion queued. Send *STATUS* to check progress."
            );
            case ALREADY_PENDING -> new TwilioWebhookResponseDto(
                    "Source deletion is already in progress. Send *STATUS* to check progress."
            );
            case NOT_FOUND_OR_DELETED -> new TwilioWebhookResponseDto(
                    "Source not found or already deleted. Send *SOURCES* to list current source IDs."
            );
        };
    }
}
