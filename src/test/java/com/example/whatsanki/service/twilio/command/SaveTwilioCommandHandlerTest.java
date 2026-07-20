package com.example.whatsanki.service.twilio.command;

import com.example.whatsanki.domain.CardId;
import com.example.whatsanki.domain.DeckId;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.CardSaveResultDto;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.service.card.CardSaveService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SaveTwilioCommandHandlerTest {

    private final CardSaveService cardSaveService = mock(CardSaveService.class);
    private final SaveTwilioCommandHandler handler = new SaveTwilioCommandHandler(cardSaveService);

    @Test
    void registersSaveAndConfirmsTheSelectedContext() {
        TwilioCommandContextDto context = context(2);
        when(cardSaveService.saveLatest(eq(context.userId()), eq(2), any(Instant.class)))
                .thenReturn(CardSaveResultDto.saved(CardId.random(), DeckId.random(), 2));

        assertThat(handler.supportedCommand()).isEqualTo(InboundMessageEntity.CommandType.SAVE);
        assertThat(handler.handle(context).reply()).isEqualTo("Saved context 2 to your *Default* deck.");
    }

    @Test
    void returnsActionableGuidanceWhenSelectionIsUnavailable() {
        TwilioCommandContextDto context = context(7);
        when(cardSaveService.saveLatest(eq(context.userId()), eq(7), any(Instant.class)))
                .thenReturn(CardSaveResultDto.unavailable(CardSaveResultDto.Outcome.INVALID_SELECTION, 7));

        assertThat(handler.handle(context).reply())
                .isEqualTo("That result number is unavailable. Use a number shown by your latest *LOOKUP*.");
    }

    private TwilioCommandContextDto context(int selection) {
        return new TwilioCommandContextDto(
                UserId.random(),
                InboundMessageId.random(),
                new ParsedTwilioCommandDto(
                        InboundMessageEntity.CommandType.SAVE,
                        Integer.toString(selection),
                        Integer.toString(selection),
                        null,
                        null
                )
        );
    }
}
