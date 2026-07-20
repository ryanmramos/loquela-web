package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.LookupResultMessageFormatter;
import com.example.whatsanki.service.exception.OutboundDeliveryException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LookupResultMessageFormatterImpl implements LookupResultMessageFormatter {
    private static final int PART_PREFIX_RESERVE = 32;
    private static final Pattern YOUTUBE_VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    private final WhatsAnkiConfigurationDto configuration;

    @Override
    public List<String> format(Map<String, Object> resultPayload) {
        try {
            Map<String, Object> dictionary = map(required(resultPayload, "dictionary"));
            List<?> senses = list(required(dictionary, "senses"));
            List<?> contexts = list(required(resultPayload, "contexts"));
            if (senses.isEmpty() || contexts.isEmpty()) {
                throw new IllegalArgumentException("Lookup result has no definition or context");
            }
            Pattern contextTermPattern = contextTermPattern(dictionary);

            List<String> blocks = new ArrayList<>();
            blocks.add("Lookup: " + text(dictionary, "lemma") + " ("
                    + text(dictionary, "sourceLanguage") + " → "
                    + text(dictionary, "definitionLanguage") + ")");
            for (int index = 0; index < senses.size(); index++) {
                Map<String, Object> sense = map(senses.get(index));
                blocks.add("Definition " + (index + 1) + " [" + text(sense, "partOfSpeech")
                        + "]: " + text(sense, "definition"));
            }
            for (Object contextValue : contexts) {
                Map<String, Object> context = map(contextValue);
                blocks.add(formatContext(context, contextTermPattern));
            }

            Map<String, Object> provenance = map(required(dictionary, "provenance"));
            blocks.add("Dictionary attribution: " + text(provenance, "attributionText"));
            return pack(blocks);
        } catch (OutboundDeliveryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OutboundDeliveryException(
                    BackgroundJobFailureKind.PERMANENT,
                    "LOOKUP_RESULT_INVALID",
                    exception
            );
        }
    }

    private String formatContext(Map<String, Object> context, Pattern contextTermPattern) {
        StringBuilder result = new StringBuilder()
                .append(number(context, "number"))
                .append(". ")
                .append(boldMatches(text(context, "displayText"), contextTermPattern))
                .append("\n— ")
                .append(text(context, "sourceDisplayName"));
        Map<String, Object> location = map(required(context, "location"));
        Object pageNumber = location.get("pageNumber");
        if (pageNumber instanceof Number page && page.intValue() > 0) {
            result.append(", p. ").append(page.intValue());
        }

        // Location JSON is deliberately source-neutral. A future subtitle source can
        // become clickable without changing lookup persistence or the delivery outbox.
        String videoId = optionalText(location.get("videoId"));
        Object startMs = location.get("startMs");
        if (videoId != null && YOUTUBE_VIDEO_ID.matcher(videoId).matches()
                && startMs instanceof Number timestamp && timestamp.longValue() >= 0) {
            result.append("\nhttps://www.youtube.com/watch?v=")
                    .append(videoId)
                    .append("&t=")
                    .append(timestamp.longValue() / 1_000)
                    .append('s');
        }
        return result.toString();
    }

    private Pattern contextTermPattern(Map<String, Object> dictionary) {
        Map<String, String> termsByFoldedValue = new LinkedHashMap<>();
        addTerm(termsByFoldedValue, text(dictionary, "requestedTerm"));
        addTerm(termsByFoldedValue, text(dictionary, "lemma"));
        for (Object form : list(required(dictionary, "forms"))) {
            if (form instanceof String text) {
                addTerm(termsByFoldedValue, text);
            }
        }

        // Longest-first alternation ensures a phrase is highlighted as one unit when
        // the provider also returns one of its individual words as an inflected form.
        String alternatives = termsByFoldedValue.values().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .reduce((left, right) -> left + "|" + right)
                .orElseThrow(() -> new IllegalArgumentException("Dictionary entry has no lookup terms"));
        return Pattern.compile(
                "(?<![\\p{L}\\p{N}\\p{M}])(?:" + alternatives + ")(?![\\p{L}\\p{N}\\p{M}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
    }

    private void addTerm(Map<String, String> termsByFoldedValue, String value) {
        String term = optionalText(value);
        if (term != null) {
            termsByFoldedValue.putIfAbsent(term.toLowerCase(Locale.ROOT), term);
        }
    }

    private String boldMatches(String context, Pattern termPattern) {
        Matcher matcher = termPattern.matcher(context);
        StringBuilder highlighted = new StringBuilder(context.length());
        while (matcher.find()) {
            matcher.appendReplacement(highlighted, Matcher.quoteReplacement("*" + matcher.group() + "*"));
        }
        matcher.appendTail(highlighted);
        return highlighted.toString();
    }

    private List<String> pack(List<String> blocks) {
        int maxCharacters = configuration.getOutbound().getMaxMessageCharacters();
        int contentLimit = maxCharacters - PART_PREFIX_RESERVE;
        List<String> fragments = blocks.stream()
                .flatMap(block -> split(block, contentLimit).stream())
                .toList();
        List<String> packed = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String fragment : fragments) {
            int separator = current.isEmpty() ? 0 : 2;
            if (!current.isEmpty()
                    && codePoints(current.toString()) + separator + codePoints(fragment) > contentLimit) {
                packed.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(fragment);
        }
        if (!current.isEmpty()) {
            packed.add(current.toString());
        }
        if (packed.size() > configuration.getOutbound().getMaxLookupParts()) {
            throw OutboundDeliveryException.permanent("OUTBOUND_MESSAGE_TOO_LARGE");
        }
        if (packed.size() == 1) {
            return List.copyOf(packed);
        }
        List<String> numbered = new ArrayList<>(packed.size());
        for (int index = 0; index < packed.size(); index++) {
            String body = "Lookup result (" + (index + 1) + "/" + packed.size() + ")\n" + packed.get(index);
            if (codePoints(body) > maxCharacters) {
                throw OutboundDeliveryException.permanent("OUTBOUND_MESSAGE_TOO_LARGE");
            }
            numbered.add(body);
        }
        return List.copyOf(numbered);
    }

    private List<String> split(String value, int limit) {
        List<String> parts = new ArrayList<>();
        int offset = 0;
        int total = codePoints(value);
        while (offset < total) {
            int count = Math.min(limit, total - offset);
            int startIndex = value.offsetByCodePoints(0, offset);
            int endIndex = value.offsetByCodePoints(startIndex, count);
            parts.add(value.substring(startIndex, endIndex));
            offset += count;
        }
        return parts;
    }

    private Object required(Map<String, Object> value, String key) {
        Objects.requireNonNull(value, "resultPayload must not be null");
        return Objects.requireNonNull(value.get(key), key + " must not be null");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return (Map<String, Object>) map;
    }

    private List<?> list(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected a JSON array");
        }
        return list;
    }

    private String text(Map<String, Object> value, String key) {
        String result = optionalText(required(value, key));
        if (result == null) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        return result;
    }

    private String optionalText(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.strip();
    }

    private int number(Map<String, Object> value, String key) {
        Object number = required(value, key);
        if (!(number instanceof Number numeric) || numeric.intValue() < 1) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return numeric.intValue();
    }

    private int codePoints(String value) {
        return value.codePointCount(0, value.length());
    }
}
