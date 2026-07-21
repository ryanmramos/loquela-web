package com.example.whatsanki.service.twilio.command;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.AccountDeletionConfirmationResultDto;
import com.example.whatsanki.dto.AccountDeletionRequestResultDto;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.service.AccountDeletionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeleteMyDataTwilioCommandHandlerTest {

    private final AccountDeletionService accountDeletionService = mock(AccountDeletionService.class);
    private final DeleteMyDataTwilioCommandHandler handler = new DeleteMyDataTwilioCommandHandler(accountDeletionService);

    @Test
    void issuesAConfirmationInstructionWithoutLoggingOrPersistingTheCode() {
        TwilioCommandContextDto context = context("REQUEST");
        when(accountDeletionService.request(context.userId()))
                .thenReturn(AccountDeletionRequestResultDto.confirmationRequired("ABCD2345", Instant.parse("2026-01-01T12:00:00Z")));

        assertThat(handler.handle(context).reply())
                .contains("DELETE MY DATA CONFIRM ABCD2345")
                .contains("12:00 UTC");
    }

    @Test
    void returnsSafeResponsesForConfirmationOutcomes() {
        TwilioCommandContextDto context = context("ABCD2345");
        when(accountDeletionService.confirm(context.userId(), "ABCD2345"))
                .thenReturn(AccountDeletionConfirmationResultDto.queued(JobId.random()));
        assertThat(handler.handle(context).reply()).contains("deletion queued");

        when(accountDeletionService.confirm(context.userId(), "ABCD2345"))
                .thenReturn(AccountDeletionConfirmationResultDto.invalidOrExpired());
        assertThat(handler.handle(context).reply()).contains("invalid or expired");
    }

    private TwilioCommandContextDto context(String normalizedValue) {
        return new TwilioCommandContextDto(UserId.random(), InboundMessageId.random(), new ParsedTwilioCommandDto(
                InboundMessageEntity.CommandType.DELETE_MY_DATA, null, normalizedValue, null, null
        ));
    }
}
