package com.example.whatsanki.service.twilio;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioMediaMetadataDto;
import com.example.whatsanki.dto.TwilioWebhookRequestDto;
import com.example.whatsanki.service.twilio.TwilioCommandParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

@Service
@Slf4j
public class TwilioCommandParserImpl implements TwilioCommandParser {

    static final String ADD_USAGE = "*ADD* requires text. Example: *ADD* La casa es grande.";
    static final String LOOKUP_USAGE = "*LOOKUP* requires a word or phrase. Example: *LOOKUP* casa.";
    static final String LANGUAGE_USAGE = "Use *LANGUAGE* <source> <definition>. Example: *LANGUAGE* es en.";
    static final String SAVE_USAGE = "Use *SAVE* <positive number>. Example: *SAVE* 1.";
    static final String DELETE_SOURCE_USAGE = "Use *DELETE SOURCE* <source-id>.";
    static final String DELETE_USAGE = "Use *DELETE SOURCE* <source-id> or *DELETE MY DATA*.";
    static final String DOCUMENT_MEDIA_USAGE = "The document attachment is missing its media URL or content type.";
    static final String NO_ARGUMENTS_USAGE = "This command does not accept additional text.";

    @Override
    public ParsedTwilioCommandDto parse(TwilioWebhookRequestDto request) {
        String displayBody = displayText(request.getBody());

        // Attachments win over body commands because Twilio uses Body as an optional
        // caption when media is present. Treating the caption as a command would lose it.
        if (request.getNumMedia() > 0) {
            ParsedTwilioCommandDto command = document(request, displayBody);
            log(command);
            return command;
        }

        CommandParts parts = splitCommand(displayBody);
        ParsedTwilioCommandDto command = switch (parts.keyword()) {
            case "HELP" -> withoutArguments(InboundMessageEntity.CommandType.HELP, parts.argument());
            case "SETTINGS" -> withoutArguments(InboundMessageEntity.CommandType.SETTINGS, parts.argument());
            case "LANGUAGE" -> language(parts.argument());
            case "ADD" -> requiredText(InboundMessageEntity.CommandType.ADD, parts.argument(), ADD_USAGE);
            case "LOOKUP" -> requiredText(InboundMessageEntity.CommandType.LOOKUP, parts.argument(), LOOKUP_USAGE);
            case "SAVE" -> save(parts.argument());
            case "SOURCES" -> withoutArguments(InboundMessageEntity.CommandType.SOURCES, parts.argument());
            case "DELETE" -> delete(parts.argument());
            case "EXPORT" -> export(parts.argument());
            case "STATUS" -> withoutArguments(InboundMessageEntity.CommandType.STATUS, parts.argument());
            default -> fallback(displayBody);
        };
        log(command);
        return command;
    }

    private ParsedTwilioCommandDto document(TwilioWebhookRequestDto request, String caption) {
        String mediaUrl = blankToNull(request.firstMediaUrl());
        String contentType = blankToNull(request.firstMediaContentType());
        String validationError = mediaUrl == null || contentType == null ? DOCUMENT_MEDIA_USAGE : null;
        TwilioMediaMetadataDto media = new TwilioMediaMetadataDto(
                request.getNumMedia(), mediaUrl, contentType, blankToNull(caption)
        );
        return command(
                InboundMessageEntity.CommandType.DOCUMENT,
                blankToNull(caption),
                contentType == null ? null : contentType.toLowerCase(Locale.ROOT),
                media,
                validationError
        );
    }

    private ParsedTwilioCommandDto language(String argument) {
        String normalizedArgument = recognitionText(argument);
        String[] tags = normalizedArgument.isEmpty() ? new String[0] : normalizedArgument.split(" ");
        if (tags.length != 2) {
            return command(InboundMessageEntity.CommandType.LANGUAGE, blankToNull(argument), null, null, LANGUAGE_USAGE);
        }
        try {
            String source = new LanguageTag(tags[0]).value();
            String definition = new LanguageTag(tags[1]).value();
            return command(
                    InboundMessageEntity.CommandType.LANGUAGE,
                    argument,
                    source + " " + definition,
                    null,
                    null
            );
        } catch (IllegalArgumentException exception) {
            return command(InboundMessageEntity.CommandType.LANGUAGE, argument, null, null, LANGUAGE_USAGE);
        }
    }

    private ParsedTwilioCommandDto save(String argument) {
        String normalizedArgument = recognitionText(argument);
        try {
            int selection = Integer.parseInt(normalizedArgument);
            if (selection < 1) {
                throw new NumberFormatException("Selection must be positive");
            }
            return command(InboundMessageEntity.CommandType.SAVE, argument, Integer.toString(selection), null, null);
        } catch (NumberFormatException exception) {
            return command(InboundMessageEntity.CommandType.SAVE, blankToNull(argument), null, null, SAVE_USAGE);
        }
    }

    private ParsedTwilioCommandDto delete(String argument) {
        CommandParts deleteParts = splitCommand(displayText(argument));
        if ("SOURCE".equals(deleteParts.keyword())) {
            return requiredText(
                    InboundMessageEntity.CommandType.DELETE_SOURCE,
                    deleteParts.argument(),
                    DELETE_SOURCE_USAGE
            );
        }
        if ("MY".equals(deleteParts.keyword()) && "DATA".equals(recognitionText(deleteParts.argument()).toUpperCase(Locale.ROOT))) {
            return command(InboundMessageEntity.CommandType.DELETE_MY_DATA, argument, "MY DATA", null, null);
        }
        return command(InboundMessageEntity.CommandType.UNKNOWN, blankToNull(argument), null, null, DELETE_USAGE);
    }

    private ParsedTwilioCommandDto export(String argument) {
        String displayArgument = blankToNull(argument);
        return command(
                InboundMessageEntity.CommandType.EXPORT,
                displayArgument,
                displayArgument == null ? null : searchText(displayArgument),
                null,
                null
        );
    }

    private ParsedTwilioCommandDto requiredText(
            InboundMessageEntity.CommandType type,
            String argument,
            String usage
    ) {
        String displayArgument = blankToNull(argument);
        return command(
                type,
                displayArgument,
                displayArgument == null ? null : searchText(displayArgument),
                null,
                displayArgument == null ? usage : null
        );
    }

    private ParsedTwilioCommandDto withoutArguments(InboundMessageEntity.CommandType type, String argument) {
        String displayArgument = blankToNull(argument);
        return command(type, displayArgument, null, null, displayArgument == null ? null : NO_ARGUMENTS_USAGE);
    }

    private ParsedTwilioCommandDto fallback(String displayBody) {
        if (!displayBody.isEmpty() && recognitionText(displayBody).split(" ").length == 1) {
            // A single unknown token is a lookup convenience alias; multi-token prose
            // stays UNKNOWN so arbitrary messages are never silently ingested.
            return command(
                    InboundMessageEntity.CommandType.LOOKUP,
                    displayBody,
                    searchText(displayBody),
                    null,
                    null
            );
        }
        return command(InboundMessageEntity.CommandType.UNKNOWN, blankToNull(displayBody), null, null, null);
    }

    private ParsedTwilioCommandDto command(
            InboundMessageEntity.CommandType type,
            String argument,
            String normalizedValue,
            TwilioMediaMetadataDto media,
            String validationError
    ) {
        return new ParsedTwilioCommandDto(type, argument, normalizedValue, media, validationError);
    }

    private CommandParts splitCommand(String displayBody) {
        if (displayBody.isEmpty()) {
            return new CommandParts("", "");
        }
        int splitAt = firstWhitespace(displayBody);
        String keyword = splitAt < 0 ? displayBody : displayBody.substring(0, splitAt);
        int argumentAt = splitAt;
        while (argumentAt >= 0
                && argumentAt < displayBody.length()
                && isUnicodeWhitespace(displayBody.charAt(argumentAt))) {
            argumentAt++;
        }
        String argument = splitAt < 0 ? "" : displayBody.substring(argumentAt);
        return new CommandParts(recognitionText(keyword).toUpperCase(Locale.ROOT), argument);
    }

    private int firstWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (isUnicodeWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private String displayText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int start = 0;
        int end = value.length();
        while (start < end && isUnicodeWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && isUnicodeWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private boolean isUnicodeWhitespace(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }

    private String recognitionText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .strip()
                .replaceAll("[\\p{Z}\\s]+", " ");
    }

    private String searchText(String value) {
        return recognitionText(value).toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void log(ParsedTwilioCommandDto command) {
        log.info(
                "Parsed Twilio command commandType={} valid={} hasMedia={}",
                command.commandType(),
                command.isValid(),
                command.hasMedia()
        );
    }

    private record CommandParts(String keyword, String argument) {
    }
}
