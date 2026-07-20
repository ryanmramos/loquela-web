package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.LookupAcceptanceDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.LookupService;
import com.example.whatsanki.service.TwilioCommandHandler;
import com.example.whatsanki.service.exception.InvalidLookupTermException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LookupTwilioCommandHandler implements TwilioCommandHandler {

    public static final String ACCEPTED_RESPONSE =
            "Lookup accepted. I will send the definition and numbered examples shortly. "
                    + "Send *STATUS* to check progress.";

    private final LookupService lookupService;

    @Override
    public InboundMessageEntity.CommandType supportedCommand() {
        return InboundMessageEntity.CommandType.LOOKUP;
    }

    @Override
    @Transactional
    public TwilioWebhookResponseDto handle(TwilioCommandContextDto context) {
        LookupAcceptanceDto accepted;
        try {
            accepted = lookupService.accept(
                    context.userId(), context.inboundMessageId(), context.command().argument()
            );
        } catch (InvalidLookupTermException exception) {
            log.info("Rejected invalid LOOKUP term userId={}", context.userId().value());
            return new TwilioWebhookResponseDto(
                    "Lookup term is invalid or too long. Use *LOOKUP* <word or short phrase>."
            );
        }
        log.info(
                "Processed LOOKUP command userId={} lookupSessionId={} sourceLanguage={} definitionLanguage={}",
                context.userId().value(),
                accepted.lookupSessionId().value(),
                accepted.languagePair().sourceLanguage(),
                accepted.languagePair().definitionLanguage()
        );
        return new TwilioWebhookResponseDto(ACCEPTED_RESPONSE);
    }
}
