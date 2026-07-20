package com.example.whatsanki.service.lookup;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

@Service
public class LookupFailureMessageFormatterImpl implements LookupFailureMessageFormatter {
    static final String GENERIC_FAILURE_MESSAGE =
            "I couldn't complete that lookup because of an unexpected problem. Please try *LOOKUP* again later.";

    private static final Map<String, String> MESSAGES_BY_ERROR_CODE = Map.ofEntries(
            Map.entry(
                    "DEFINITION_NOT_FOUND",
                    "I couldn't find a definition for that term. Check the spelling or try another word or phrase."
            ),
            Map.entry(
                    "CONTEXT_NOT_FOUND",
                    "I found a definition, but no examples in your ready sources. Add matching "
                            + "source-language text with *ADD* <text> or upload a text-layer PDF, then try again."
            ),
            Map.entry(
                    "UNSUPPORTED_DICTIONARY_PAIR",
                    "That language pair isn't supported by the dictionary provider. Send *SETTINGS* to review "
                            + "your current languages, then use *LANGUAGE* <source> <definition> if needed."
            ),
            Map.entry(
                    "PROVIDER_TIMEOUT",
                    "The dictionary provider timed out after retrying. Please try *LOOKUP* again later."
            ),
            Map.entry(
                    "PROVIDER_RATE_LIMITED",
                    "The dictionary provider is temporarily busy. Please try *LOOKUP* again later."
            ),
            Map.entry(
                    "PROVIDER_TEMPORARY_FAILURE",
                    "The dictionary provider is temporarily unavailable. Please try *LOOKUP* again later."
            ),
            Map.entry(
                    "PROVIDER_REQUEST_REJECTED",
                    "The dictionary provider couldn't process that lookup. Check your term and *SETTINGS*, then try again."
            )
    );

    @Override
    public String format(String errorCode) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        return MESSAGES_BY_ERROR_CODE.getOrDefault(errorCode, GENERIC_FAILURE_MESSAGE);
    }
}
