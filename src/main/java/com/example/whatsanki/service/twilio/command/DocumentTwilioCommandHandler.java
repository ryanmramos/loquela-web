package com.example.whatsanki.service.twilio.command;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.DocumentAcceptanceDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.document.DocumentIngestionService;
import com.example.whatsanki.service.TwilioCommandHandler;
import com.example.whatsanki.service.exception.MediaAcquisitionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentTwilioCommandHandler implements TwilioCommandHandler {

    static final String ACCEPTED_RESPONSE =
            "Document accepted for secure storage. Send *STATUS* to check progress.";
    static final String UNSUPPORTED_RESPONSE =
            "Send one text-layer PDF, or use *ADD* <text> for pasted text.";

    private final DocumentIngestionService documentIngestionService;

    @Override
    public InboundMessageEntity.CommandType supportedCommand() {
        return InboundMessageEntity.CommandType.DOCUMENT;
    }

    @Override
    public TwilioWebhookResponseDto handle(TwilioCommandContextDto context) {
        try {
            DocumentAcceptanceDto accepted = documentIngestionService.accept(
                    context.userId(), context.inboundMessageId(), context.command().media()
            );
            log.info("Processed document command userId={} sourceId={}",
                    context.userId().value(), accepted.sourceId().value());
            return new TwilioWebhookResponseDto(ACCEPTED_RESPONSE);
        } catch (MediaAcquisitionException exception) {
            log.info("Rejected document command userId={} errorCode={}",
                    context.userId().value(), exception.errorCode());
            return new TwilioWebhookResponseDto(UNSUPPORTED_RESPONSE);
        }
    }
}
