package com.example.whatsanki.service.twilio.command;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.DeckExportAcceptanceDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.export.DeckExportService;
import com.example.whatsanki.service.TwilioCommandHandler;
import com.example.whatsanki.service.exception.DeckExportException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportTwilioCommandHandler implements TwilioCommandHandler {
    private final DeckExportService deckExportService;

    @Override
    public InboundMessageEntity.CommandType supportedCommand() {
        return InboundMessageEntity.CommandType.EXPORT;
    }

    @Override
    public TwilioWebhookResponseDto handle(TwilioCommandContextDto context) {
        try {
            DeckExportAcceptanceDto accepted = deckExportService.accept(
                    context.userId(), context.command().argument(), context.command().normalizedValue()
            );
            log.info("Processed EXPORT command userId={} exportRequestId={} cardCount={}",
                    context.userId().value(), accepted.exportRequestId().value(), accepted.cardCount());
            return new TwilioWebhookResponseDto(
                    "Export queued for *" + accepted.deckName() + "* (" + accepted.cardCount()
                            + " cards). I will send a private download link shortly."
            );
        } catch (DeckExportException exception) {
            log.info("Rejected EXPORT command userId={} errorCode={}",
                    context.userId().value(), exception.errorCode());
            return new TwilioWebhookResponseDto(message(exception.errorCode()));
        }
    }

    private String message(String errorCode) {
        return switch (errorCode) {
            case "DECK_NOT_FOUND" -> "I couldn't find that deck. Use *EXPORT* with no name for your *Default* deck.";
            case "DECK_EMPTY" -> "That deck has no cards yet. Use *LOOKUP* and *SAVE* before exporting.";
            case "CARD_TEMPLATE_UNSUPPORTED" -> "That deck contains older cards that cannot be exported. Save those lookup results again.";
            case "CARD_SNAPSHOT_INVALID" -> "One or more cards cannot be exported safely. Your cards were not changed.";
            default -> "I couldn't queue that export. Your cards are safe; try *EXPORT* again.";
        };
    }
}
