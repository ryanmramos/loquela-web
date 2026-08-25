package com.example.whatsanki.service.util;

import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.dto.PassageDraftDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits normalized source text into bounded, ordered passages with source locations. */
@Component
@RequiredArgsConstructor
public class PassageSegmenterImpl implements PassageSegmenter {

    private static final Pattern TOKEN = Pattern.compile(
            "[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)?",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    private final WhatsAnkiConfigurationDto configuration;

    @Override
    public List<PassageDraftDto> segment(String displayText, LanguageTag language) {
        if (displayText == null) {
            throw new IllegalArgumentException("displayText must not be null");
        }
        if (language == null) {
            throw new IllegalArgumentException("language must not be null");
        }
        if (displayText.isBlank()) {
            return List.of();
        }

        List<PassageDraftDto> passages = new ArrayList<>();
        Locale locale = Locale.forLanguageTag(language.value());
        int paragraphStart = 0;
        int paragraphIndex = 0;
        while (paragraphStart < displayText.length()) {
            int separator = displayText.indexOf("\n\n", paragraphStart);
            int paragraphEnd = separator < 0 ? displayText.length() : separator;
            segmentParagraph(displayText, paragraphStart, paragraphEnd, paragraphIndex, locale, passages);
            if (separator < 0) {
                break;
            }
            paragraphStart = separator + 2;
            paragraphIndex++;
        }
        return List.copyOf(passages);
    }

    private void segmentParagraph(
            String text,
            int paragraphStart,
            int paragraphEnd,
            int paragraphIndex,
            Locale locale,
            List<PassageDraftDto> passages
    ) {
        // Sentence boundaries improve readability. Oversized sentences are split only
        // after the locale-aware pass cannot fit them within configured limits.
        String paragraph = text.substring(paragraphStart, paragraphEnd);
        BreakIterator sentences = BreakIterator.getSentenceInstance(locale);
        sentences.setText(paragraph);

        // Keep locale-aware sentence boundaries when possible. A single sentence that is
        // larger than a configured passage limit is split separately into bounded pieces.
        List<TextSpan> pieces = new ArrayList<>();
        int sentenceStart = sentences.first();
        for (int sentenceEnd = sentences.next(); sentenceEnd != BreakIterator.DONE; sentenceEnd = sentences.next()) {
            int start = trimStart(text, paragraphStart + sentenceStart, paragraphStart + sentenceEnd);
            int end = trimEnd(text, start, paragraphStart + sentenceEnd);
            if (start < end) {
                pieces.addAll(splitOversizedSpan(text, start, end));
            }
            sentenceStart = sentenceEnd;
        }

        int passageStart = -1;
        int passageEnd = -1;
        int passageTokens = 0;
        int passageSentences = 0;
        // Greedily group adjacent pieces without crossing a paragraph boundary. This keeps
        // passages readable while preserving deterministic source order and location data.
        for (TextSpan piece : pieces) {
            int proposedCharacters = passageStart < 0
                    ? codePointLength(text, piece.start(), piece.end())
                    : codePointLength(text, passageStart, piece.end());
            boolean exceedsLimit = passageStart >= 0
                    && (passageSentences >= configuration.getIngestion().getMaxPassageSentences()
                    || passageTokens + piece.tokenCount() > configuration.getIngestion().getMaxPassageTokens()
                    || proposedCharacters > configuration.getIngestion().getMaxPassageCharacters());
            if (exceedsLimit) {
                passages.add(toDraft(text, passageStart, passageEnd, paragraphIndex, passageTokens));
                passageStart = -1;
                passageTokens = 0;
                passageSentences = 0;
            }

            if (passageStart < 0) {
                passageStart = piece.start();
            }
            passageEnd = piece.end();
            passageTokens += piece.tokenCount();
            passageSentences++;
        }
        if (passageStart >= 0) {
            passages.add(toDraft(text, passageStart, passageEnd, paragraphIndex, passageTokens));
        }
    }

    private List<TextSpan> splitOversizedSpan(String text, int spanStart, int spanEnd) {
        List<TextSpan> pieces = new ArrayList<>();
        int cursor = spanStart;
        while (cursor < spanEnd) {
            int candidateEnd = offsetByCodePoints(
                    text,
                    cursor,
                    spanEnd,
                    configuration.getIngestion().getMaxPassageCharacters()
            );
            candidateEnd = Math.min(candidateEnd, tokenLimitEnd(text, cursor, spanEnd));

            if (candidateEnd < spanEnd) {
                int whitespaceBreak = lastWhitespace(text, cursor, candidateEnd);
                if (whitespaceBreak > cursor) {
                    candidateEnd = whitespaceBreak;
                }
            }
            if (candidateEnd <= cursor) {
                candidateEnd = offsetByCodePoints(text, cursor, spanEnd, 1);
            }

            int pieceStart = trimStart(text, cursor, candidateEnd);
            int pieceEnd = trimEnd(text, pieceStart, candidateEnd);
            int tokens = tokenCount(text, pieceStart, pieceEnd);
            if (pieceStart < pieceEnd && tokens > 0) {
                pieces.add(new TextSpan(pieceStart, pieceEnd, tokens));
            }
            cursor = trimStart(text, candidateEnd, spanEnd);
        }
        return pieces;
    }

    private int tokenLimitEnd(String text, int start, int end) {
        Matcher matcher = TOKEN.matcher(text).region(start, end);
        int count = 0;
        while (matcher.find()) {
            count++;
            if (count > configuration.getIngestion().getMaxPassageTokens()) {
                return matcher.start();
            }
        }
        return end;
    }

    private int tokenCount(String text, int start, int end) {
        Matcher matcher = TOKEN.matcher(text).region(start, end);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private PassageDraftDto toDraft(String text, int start, int end, int paragraphIndex, int tokenCount) {
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("paragraphIndex", paragraphIndex);
        location.put("startCharacter", text.codePointCount(0, start));
        location.put("endCharacter", text.codePointCount(0, end));
        return new PassageDraftDto(text.substring(start, end), location, tokenCount);
    }

    private int offsetByCodePoints(String text, int start, int end, int maximumCodePoints) {
        int available = text.codePointCount(start, end);
        return text.offsetByCodePoints(start, Math.min(available, maximumCodePoints));
    }

    private int codePointLength(String text, int start, int end) {
        return text.codePointCount(start, end);
    }

    private int lastWhitespace(String text, int start, int end) {
        for (int index = end; index > start; index--) {
            if (Character.isWhitespace(text.charAt(index - 1))) {
                return index - 1;
            }
        }
        return -1;
    }

    private int trimStart(String text, int start, int end) {
        int index = start;
        while (index < end) {
            int codePoint = text.codePointAt(index);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            index += Character.charCount(codePoint);
        }
        return index;
    }

    private int trimEnd(String text, int start, int end) {
        int index = end;
        while (index > start) {
            int codePoint = text.codePointBefore(index);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            index -= Character.charCount(codePoint);
        }
        return index;
    }

    private record TextSpan(int start, int end, int tokenCount) {
    }
}
