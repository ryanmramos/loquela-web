package com.example.whatsanki.service.twilio.command;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.service.UserLanguageSettingsService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsTwilioCommandHandlerTest {

    private final UserLanguageSettingsService userLanguageSettingsService =
            mock(UserLanguageSettingsService.class);
    private final SettingsTwilioCommandHandler handler =
            new SettingsTwilioCommandHandler(userLanguageSettingsService);

    @Test
    void registersForSettingsCommand() {
        assertThat(handler.supportedCommand()).isEqualTo(InboundMessageEntity.CommandType.SETTINGS);
    }

    @Test
    void displaysTheRequestingUsersCurrentLanguagePairAndChangeGuidance() {
        TwilioCommandContextDto context = context();
        when(userLanguageSettingsService.getCurrent(context.userId()))
                .thenReturn(new LanguagePairDto("es", "en"));

        assertThat(handler.handle(context).reply()).isEqualTo("""
                Current language pair:
                Source language: es
                Definition language: en

                Change it with *LANGUAGE* <source> <definition>.""");
        verify(userLanguageSettingsService).getCurrent(context.userId());
    }

    private TwilioCommandContextDto context() {
        return new TwilioCommandContextDto(
                UserId.random(),
                InboundMessageId.random(),
                new ParsedTwilioCommandDto(
                        InboundMessageEntity.CommandType.SETTINGS,
                        null,
                        null,
                        null,
                        null
                )
        );
    }
}
