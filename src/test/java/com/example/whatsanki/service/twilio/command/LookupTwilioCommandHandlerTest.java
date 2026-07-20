package com.example.whatsanki.service.twilio.command;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LookupAcceptanceDto;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.service.lookup.LookupService;
import com.example.whatsanki.service.exception.InvalidLookupTermException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LookupTwilioCommandHandlerTest {

    @Mock private LookupService lookupService;
    @InjectMocks private LookupTwilioCommandHandler handler;

    @Test
    void registersLookupAndReturnsFastAcceptedResponse() {
        TwilioCommandContextDto context = context("Vivir");
        when(lookupService.accept(
                context.userId(), context.inboundMessageId(), "Vivir"
        )).thenReturn(new LookupAcceptanceDto(
                LookupSessionId.random(),
                new LanguagePairDto("es", "en"),
                Instant.now().plusSeconds(1_800)
        ));

        assertThat(handler.supportedCommand()).isEqualTo(InboundMessageEntity.CommandType.LOOKUP);
        assertThat(handler.handle(context).reply()).isEqualTo(LookupTwilioCommandHandler.ACCEPTED_RESPONSE);
        verify(lookupService).accept(context.userId(), context.inboundMessageId(), "Vivir");
    }

    @Test
    void returnsSafeGuidanceForInvalidTerm() {
        TwilioCommandContextDto context = context("invalid");
        when(lookupService.accept(
                context.userId(), context.inboundMessageId(), "invalid"
        )).thenThrow(new InvalidLookupTermException("internal validation detail"));

        assertThat(handler.handle(context).reply())
                .isEqualTo("Lookup term is invalid or too long. Use *LOOKUP* <word or short phrase>.");
    }

    private TwilioCommandContextDto context(String term) {
        return new TwilioCommandContextDto(
                UserId.random(),
                InboundMessageId.random(),
                new ParsedTwilioCommandDto(
                        InboundMessageEntity.CommandType.LOOKUP,
                        term,
                        term.toLowerCase(),
                        null,
                        null
                )
        );
    }
}
