package com.example.whatsanki.service.twilio;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioWebhookRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TwilioCommandParserImplTest {

    private TwilioCommandParserImpl parser;

    @BeforeEach
    void setUp() {
        parser = new TwilioCommandParserImpl();
    }

    @ParameterizedTest
    @MethodSource("recognizedCommands")
    void recognizesCompleteCommandVocabulary(String body, InboundMessageEntity.CommandType expectedType) {
        ParsedTwilioCommandDto command = parser.parse(request(body));

        assertThat(command.commandType()).isEqualTo(expectedType);
        assertThat(command.isValid()).isTrue();
    }

    @Test
    void preservesUnicodeAndOriginalArgumentCasingWhileNormalizingLookupValue() {
        ParsedTwilioCommandDto command = parser.parse(request("  LoOkUp  Árbol DE la Vida  "));

        assertThat(command.commandType()).isEqualTo(InboundMessageEntity.CommandType.LOOKUP);
        assertThat(command.argument()).isEqualTo("Árbol DE la Vida");
        assertThat(command.normalizedValue()).isEqualTo("árbol de la vida");
    }

    @Test
    void canonicalizesLanguageTags() {
        ParsedTwilioCommandDto command = parser.parse(request("LANGUAGE ES-latn-us en-us"));

        assertThat(command.commandType()).isEqualTo(InboundMessageEntity.CommandType.LANGUAGE);
        assertThat(command.argument()).isEqualTo("ES-latn-us en-us");
        assertThat(command.normalizedValue()).isEqualTo("es-Latn-US en-US");
        assertThat(command.isValid()).isTrue();
    }

    @Test
    void parsesTheAccountDeletionConfirmationCodeWithoutRetainingItInTheCommandArgument() {
        ParsedTwilioCommandDto command = parser.parse(request("DELETE MY DATA CONFIRM abcd2345"));

        assertThat(command.commandType()).isEqualTo(InboundMessageEntity.CommandType.DELETE_MY_DATA);
        assertThat(command.normalizedValue()).isEqualTo("ABCD2345");
        assertThat(command.isValid()).isTrue();
    }

    @Test
    void treatsUnknownSingleTokenAsLookupAlias() {
        ParsedTwilioCommandDto command = parser.parse(request("Überraschung"));

        assertThat(command.commandType()).isEqualTo(InboundMessageEntity.CommandType.LOOKUP);
        assertThat(command.argument()).isEqualTo("Überraschung");
        assertThat(command.normalizedValue()).isEqualTo("überraschung");
    }

    @Test
    void recognizesCommandSeparatedByUnicodeWhitespace() {
        ParsedTwilioCommandDto command = parser.parse(request("ADD\u00a0Texto con espacio no separable"));

        assertThat(command.commandType()).isEqualTo(InboundMessageEntity.CommandType.ADD);
        assertThat(command.argument()).isEqualTo("Texto con espacio no separable");
    }

    @Test
    void treatsAmbiguousProseAsUnknown() {
        ParsedTwilioCommandDto command = parser.parse(request("save this sentence for later"));

        assertThat(command.commandType()).isEqualTo(InboundMessageEntity.CommandType.SAVE);
        assertThat(command.isValid()).isFalse();
        assertThat(command.validationError()).isEqualTo(TwilioCommandParserImpl.SAVE_USAGE);
    }

    @Test
    void treatsUnrecognizedMultiwordProseAsUnknown() {
        ParsedTwilioCommandDto command = parser.parse(request("this is ordinary prose"));

        assertThat(command.commandType()).isEqualTo(InboundMessageEntity.CommandType.UNKNOWN);
        assertThat(command.isValid()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidCommands")
    void returnsHelpfulValidationErrors(String body, InboundMessageEntity.CommandType type, String expectedError) {
        ParsedTwilioCommandDto command = parser.parse(request(body));

        assertThat(command.commandType()).isEqualTo(type);
        assertThat(command.isValid()).isFalse();
        assertThat(command.validationError()).isEqualTo(expectedError);
    }

    @Test
    void attachmentTakesPrecedenceAndRetainsBodyAsCaption() {
        TwilioWebhookRequestDto request = request("HELP – capítulo Uno");
        request.setNumMedia(1);
        request.setMediaUrl0("https://api.twilio.com/media/ME123");
        request.setMediaContentType0("application/pdf");

        ParsedTwilioCommandDto command = parser.parse(request);

        assertThat(command.commandType()).isEqualTo(InboundMessageEntity.CommandType.DOCUMENT);
        assertThat(command.argument()).isEqualTo("HELP – capítulo Uno");
        assertThat(command.hasMedia()).isTrue();
        assertThat(command.media().count()).isEqualTo(1);
        assertThat(command.media().url()).isEqualTo("https://api.twilio.com/media/ME123");
        assertThat(command.media().contentType()).isEqualTo("application/pdf");
        assertThat(command.media().caption()).isEqualTo("HELP – capítulo Uno");
    }

    @Test
    void rejectsIncompleteAttachmentMetadata() {
        TwilioWebhookRequestDto request = request("chapter one");
        request.setNumMedia(1);
        request.setMediaUrl0("https://api.twilio.com/media/ME123");

        ParsedTwilioCommandDto command = parser.parse(request);

        assertThat(command.commandType()).isEqualTo(InboundMessageEntity.CommandType.DOCUMENT);
        assertThat(command.validationError()).isEqualTo(TwilioCommandParserImpl.DOCUMENT_MEDIA_USAGE);
    }

    private static Stream<Arguments> recognizedCommands() {
        return Stream.of(
                Arguments.of("help", InboundMessageEntity.CommandType.HELP),
                Arguments.of("SETTINGS", InboundMessageEntity.CommandType.SETTINGS),
                Arguments.of("LANGUAGE es en", InboundMessageEntity.CommandType.LANGUAGE),
                Arguments.of("ADD Érase una vez", InboundMessageEntity.CommandType.ADD),
                Arguments.of("LOOKUP tener ganas de", InboundMessageEntity.CommandType.LOOKUP),
                Arguments.of("YOUTUBE https://youtu.be/dQw4w9WgXcQ", InboundMessageEntity.CommandType.YOUTUBE),
                Arguments.of("SAVE 2", InboundMessageEntity.CommandType.SAVE),
                Arguments.of("SOURCES", InboundMessageEntity.CommandType.SOURCES),
                Arguments.of("DELETE SOURCE a1b2c3", InboundMessageEntity.CommandType.DELETE_SOURCE),
                Arguments.of("EXPORT Spanish Reading", InboundMessageEntity.CommandType.EXPORT),
                Arguments.of("EXPORT", InboundMessageEntity.CommandType.EXPORT),
                Arguments.of("STATUS", InboundMessageEntity.CommandType.STATUS),
                Arguments.of("DELETE MY DATA", InboundMessageEntity.CommandType.DELETE_MY_DATA)
        );
    }

    private static Stream<Arguments> invalidCommands() {
        return Stream.of(
                Arguments.of("ADD", InboundMessageEntity.CommandType.ADD, TwilioCommandParserImpl.ADD_USAGE),
                Arguments.of("LOOKUP", InboundMessageEntity.CommandType.LOOKUP, TwilioCommandParserImpl.LOOKUP_USAGE),
                Arguments.of("YOUTUBE", InboundMessageEntity.CommandType.YOUTUBE, TwilioCommandParserImpl.YOUTUBE_USAGE),
                Arguments.of("LANGUAGE es", InboundMessageEntity.CommandType.LANGUAGE, TwilioCommandParserImpl.LANGUAGE_USAGE),
                Arguments.of("LANGUAGE invalid_tag en", InboundMessageEntity.CommandType.LANGUAGE, TwilioCommandParserImpl.LANGUAGE_USAGE),
                Arguments.of("SAVE 0", InboundMessageEntity.CommandType.SAVE, TwilioCommandParserImpl.SAVE_USAGE),
                Arguments.of("SAVE one", InboundMessageEntity.CommandType.SAVE, TwilioCommandParserImpl.SAVE_USAGE),
                Arguments.of("DELETE SOURCE", InboundMessageEntity.CommandType.DELETE_SOURCE, TwilioCommandParserImpl.DELETE_SOURCE_USAGE),
                Arguments.of("DELETE everything", InboundMessageEntity.CommandType.UNKNOWN, TwilioCommandParserImpl.DELETE_USAGE),
                Arguments.of("DELETE MY DATA CONFIRM nope", InboundMessageEntity.CommandType.DELETE_MY_DATA,
                        TwilioCommandParserImpl.DELETE_MY_DATA_CONFIRMATION_USAGE),
                Arguments.of("HELP now", InboundMessageEntity.CommandType.HELP, TwilioCommandParserImpl.NO_ARGUMENTS_USAGE)
        );
    }

    private TwilioWebhookRequestDto request(String body) {
        TwilioWebhookRequestDto request = new TwilioWebhookRequestDto();
        request.setMessageSid("SM123");
        request.setFrom("whatsapp:+15550000001");
        request.setTo("whatsapp:+15550000002");
        request.setBody(body);
        return request;
    }
}
