package com.example.whatsanki.service.twilio;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.AccountDeletionService;
import com.example.whatsanki.service.TwilioCommandHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Applies deletion-state command restrictions before dispatching registered handlers. */
@Service
@Slf4j
public class TwilioCommandServiceImpl implements TwilioCommandService {

    static final String UNKNOWN_COMMAND_RESPONSE =
            "Send *HELP* to see the supported commands. Use *ADD* <text> to save pasted text.";
    static final String NOT_IMPLEMENTED_RESPONSE =
            "That command is recognized but is coming in a later milestone.";
    static final String ACCOUNT_DELETION_IN_PROGRESS_RESPONSE =
            "Account deletion is in progress. Only *STATUS*, *HELP*, and *DELETE MY DATA* are available.";

    private final Map<InboundMessageEntity.CommandType, TwilioCommandHandler> handlers;
    private final AccountDeletionService accountDeletionService;

    public TwilioCommandServiceImpl(
            List<TwilioCommandHandler> registeredHandlers,
            AccountDeletionService accountDeletionService
    ) {
        this.accountDeletionService = accountDeletionService;
        EnumMap<InboundMessageEntity.CommandType, TwilioCommandHandler> mappedHandlers =
                new EnumMap<>(InboundMessageEntity.CommandType.class);
        for (TwilioCommandHandler handler : registeredHandlers) {
            TwilioCommandHandler duplicate = mappedHandlers.put(handler.supportedCommand(), handler);
            if (duplicate != null) {
                throw new IllegalStateException(
                        "Multiple Twilio command handlers registered for " + handler.supportedCommand()
                );
            }
        }
        handlers = Map.copyOf(mappedHandlers);
        log.info("Registered Twilio command handlers count={}", handlers.size());
    }

    /** Returns safe user-facing responses for invalid, unknown, and unavailable commands. */
    @Override
    @Transactional
    public TwilioWebhookResponseDto handle(TwilioCommandContextDto context) {
        ParsedTwilioCommandDto command = context.command();
        if (!command.isValid()) {
            log.info(
                    "Rejected invalid command userId={} commandType={}",
                    context.userId().value(),
                    command.commandType()
            );
            return new TwilioWebhookResponseDto(command.validationError());
        }
        if (command.commandType() == InboundMessageEntity.CommandType.UNKNOWN) {
            log.info("Received ambiguous or unknown command userId={}", context.userId().value());
            return new TwilioWebhookResponseDto(UNKNOWN_COMMAND_RESPONSE);
        }
        if (accountDeletionService.isDeletionInProgress(context.userId())
                && command.commandType() != InboundMessageEntity.CommandType.STATUS
                && command.commandType() != InboundMessageEntity.CommandType.HELP
                && command.commandType() != InboundMessageEntity.CommandType.DELETE_MY_DATA) {
            log.info("Rejected command while account deletion is in progress userId={} commandType={}",
                    context.userId().value(), command.commandType());
            return new TwilioWebhookResponseDto(ACCOUNT_DELETION_IN_PROGRESS_RESPONSE);
        }

        TwilioCommandHandler handler = handlers.get(command.commandType());
        if (handler == null) {
            log.info(
                    "Received recognized command without handler userId={} commandType={}",
                    context.userId().value(),
                    command.commandType()
            );
            return new TwilioWebhookResponseDto(NOT_IMPLEMENTED_RESPONSE);
        }

        log.info(
                "Dispatching Twilio command userId={} commandType={}",
                context.userId().value(),
                command.commandType()
        );
        return handler.handle(context);
    }
}
