package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.PastedTextAcceptanceDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.PastedTextIngestionService;
import com.example.whatsanki.service.exception.PastedTextLimitExceededException;
import com.example.whatsanki.service.TwilioCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddTwilioCommandHandler implements TwilioCommandHandler {

    public static final String ACCEPTED_RESPONSE = "Text accepted for ingestion. Send STATUS to check progress.";

    private final PastedTextIngestionService pastedTextIngestionService;

    @Override
    public InboundMessageEntity.CommandType supportedCommand() {
        return InboundMessageEntity.CommandType.ADD;
    }

    @Override
    @Transactional
    public TwilioWebhookResponseDto handle(TwilioCommandContextDto context) {
        try {
            PastedTextAcceptanceDto accepted = pastedTextIngestionService.accept(
                    context.userId(),
                    context.inboundMessageId(),
                    context.command().argument()
            );
            log.info(
                    "Processed ADD command userId={} sourceId={} characterCount={}",
                    context.userId().value(), accepted.sourceId().value(), accepted.characterCount()
            );
            return new TwilioWebhookResponseDto(ACCEPTED_RESPONSE);
        } catch (PastedTextLimitExceededException exception) {
            log.info(
                    "Rejected oversized ADD command userId={} maximumCharacters={}",
                    context.userId().value(), exception.maximumCharacters()
            );
            return new TwilioWebhookResponseDto(
                    "Text is too long. Send at most " + exception.maximumCharacters() + " characters with ADD."
            );
        }
    }
}
