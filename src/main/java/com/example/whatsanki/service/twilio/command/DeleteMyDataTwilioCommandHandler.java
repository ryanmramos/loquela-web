package com.example.whatsanki.service.twilio.command;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.AccountDeletionConfirmationResultDto;
import com.example.whatsanki.dto.AccountDeletionRequestResultDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.AccountDeletionService;
import com.example.whatsanki.service.TwilioCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteMyDataTwilioCommandHandler implements TwilioCommandHandler {

    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final AccountDeletionService accountDeletionService;

    @Override
    public InboundMessageEntity.CommandType supportedCommand() {
        return InboundMessageEntity.CommandType.DELETE_MY_DATA;
    }

    @Override
    public TwilioWebhookResponseDto handle(TwilioCommandContextDto context) {
        if ("REQUEST".equals(context.command().normalizedValue())) {
            return requestConfirmation(context);
        }
        return confirmDeletion(context);
    }

    private TwilioWebhookResponseDto requestConfirmation(TwilioCommandContextDto context) {
        AccountDeletionRequestResultDto result = accountDeletionService.request(context.userId());
        log.info("Processed DELETE MY DATA request userId={} outcome={}", context.userId().value(), result.outcome());
        if (result.outcome() == AccountDeletionRequestResultDto.Outcome.DELETION_IN_PROGRESS) {
            return new TwilioWebhookResponseDto("Account deletion is already in progress. Send *STATUS* to check progress.");
        }
        return new TwilioWebhookResponseDto(
                "This permanently deletes your WhatsAnki data. Reply *DELETE MY DATA CONFIRM "
                        + result.confirmationCode() + "* by " + EXPIRY_FORMAT.format(result.expiresAt()) + "."
        );
    }

    private TwilioWebhookResponseDto confirmDeletion(TwilioCommandContextDto context) {
        AccountDeletionConfirmationResultDto result = accountDeletionService.confirm(
                context.userId(), context.command().normalizedValue()
        );
        log.info("Processed DELETE MY DATA confirmation userId={} outcome={}", context.userId().value(), result.outcome());
        return switch (result.outcome()) {
            case QUEUED -> new TwilioWebhookResponseDto(
                    "Account deletion queued. Your documents, decks, cards, and settings will be removed."
            );
            case INVALID_OR_EXPIRED -> new TwilioWebhookResponseDto(
                    "That deletion confirmation is invalid or expired. Send *DELETE MY DATA* to request a new code."
            );
            case DELETION_IN_PROGRESS -> new TwilioWebhookResponseDto(
                    "Account deletion is already in progress. Send *STATUS* to check progress."
            );
        };
    }
}
