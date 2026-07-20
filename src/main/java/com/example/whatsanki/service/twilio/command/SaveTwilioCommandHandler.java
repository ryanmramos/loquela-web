package com.example.whatsanki.service.twilio.command;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.CardSaveResultDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.card.CardSaveService;
import com.example.whatsanki.service.TwilioCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class SaveTwilioCommandHandler implements TwilioCommandHandler {

    private final CardSaveService cardSaveService;

    @Override
    public InboundMessageEntity.CommandType supportedCommand() {
        return InboundMessageEntity.CommandType.SAVE;
    }

    @Override
    public TwilioWebhookResponseDto handle(TwilioCommandContextDto context) {
        int contextNumber = Integer.parseInt(context.command().normalizedValue());
        CardSaveResultDto result = cardSaveService.saveLatest(
                context.userId(), contextNumber, Instant.now()
        );
        log.info(
                "Processed SAVE command userId={} contextNumber={} outcome={}",
                context.userId().value(), contextNumber, result.outcome()
        );
        return new TwilioWebhookResponseDto(message(result));
    }

    private String message(CardSaveResultDto result) {
        return switch (result.outcome()) {
            case SAVED -> "Saved context " + result.contextNumber() + " to your *Default* deck.";
            case ALREADY_SAVED -> "Context " + result.contextNumber()
                    + " from that lookup is already saved in your *Default* deck.";
            case NO_LOOKUP -> "There is no lookup to save. Send *LOOKUP* <word or phrase> first.";
            case LOOKUP_PENDING -> "Your latest lookup is still running. Send *STATUS* to check progress.";
            case LOOKUP_EXPIRED -> "Your latest lookup has expired. Send *LOOKUP* again before using *SAVE*.";
            case LOOKUP_FAILED -> "Your latest lookup did not complete. Send *LOOKUP* again before using *SAVE*.";
            case INVALID_SELECTION -> "That result number is unavailable. Use a number shown by your latest *LOOKUP*.";
            case INVALID_SNAPSHOT -> "That lookup cannot be saved. Send *LOOKUP* again and retry *SAVE*.";
        };
    }
}
