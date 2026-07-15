package com.example.whatsanki.service.impl;

import com.example.whatsanki.service.TextNormalizer;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class TextNormalizerImpl implements TextNormalizer {

    private static final Pattern WRAPPED_WORD = Pattern.compile("(\\p{L}{2,})-\\n(?=\\p{Ll})");
    private static final Pattern HORIZONTAL_WHITESPACE = Pattern.compile("[\\p{Zs}\\t\\f\\x0B]+");
    private static final Pattern ALL_WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    @Override
    public String normalizeDisplay(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }

        String lineNormalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String safeControls = replaceDisallowedControls(lineNormalized);
        String repairedWraps = WRAPPED_WORD.matcher(safeControls).replaceAll("$1");

        // A single line break is normally visual wrapping, while an empty line is a
        // meaningful paragraph boundary. Processing line-by-line keeps that distinction.
        List<String> paragraphs = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        for (String line : repairedWraps.split("\n", -1)) {
            String normalizedLine = HORIZONTAL_WHITESPACE.matcher(line).replaceAll(" ").strip();
            if (normalizedLine.isEmpty()) {
                finishParagraph(paragraphs, paragraph);
            } else {
                if (!paragraph.isEmpty()) {
                    paragraph.append(' ');
                }
                paragraph.append(normalizedLine);
            }
        }
        finishParagraph(paragraphs, paragraph);
        return String.join("\n\n", paragraphs);
    }

    @Override
    public String normalizeForSearch(String displayText) {
        if (displayText == null) {
            throw new IllegalArgumentException("displayText must not be null");
        }
        String compatibleUnicode = Normalizer.normalize(displayText, Normalizer.Form.NFKC);
        String safeControls = replaceDisallowedControls(compatibleUnicode);
        return ALL_WHITESPACE.matcher(safeControls)
                .replaceAll(" ")
                .strip()
                .toLowerCase(Locale.ROOT);
    }

    private String replaceDisallowedControls(String value) {
        StringBuilder safe = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (codePoint == '\n' || codePoint == '\t') {
                safe.appendCodePoint(codePoint);
            } else if (Character.getType(codePoint) == Character.CONTROL) {
                safe.append(' ');
            } else {
                safe.appendCodePoint(codePoint);
            }
        });
        return safe.toString();
    }

    private void finishParagraph(List<String> paragraphs, StringBuilder paragraph) {
        if (!paragraph.isEmpty()) {
            paragraphs.add(paragraph.toString());
            paragraph.setLength(0);
        }
    }
}
