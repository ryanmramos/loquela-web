package com.example.whatsanki.service.timedtranscript;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** Removes caption markup without treating arbitrary angle-bracket text as HTML. */
@Component
public class CaptionTextCleaner {

    public Result clean(String input) {
        StringBuilder plain = new StringBuilder(input.length());
        boolean strippedMarkup = false;
        for (int index = 0; index < input.length();) {
            char character = input.charAt(index);
            if (character == '<') {
                int close = input.indexOf('>', index + 1);
                if (close >= 0 && isCaptionTag(input.substring(index + 1, close))) {
                    strippedMarkup = true;
                    index = close + 1;
                    continue;
                }
            }
            plain.append(character);
            index++;
        }
        String decoded = HtmlUtils.htmlUnescape(plain.toString())
                .replaceAll("[\\p{Z}\\s]+", " ").strip();
        return new Result(decoded, strippedMarkup);
    }

    private boolean isCaptionTag(String body) {
        String value = body.strip();
        if (value.startsWith("/")) value = value.substring(1).stripLeading();
        if (value.isEmpty()) return false;
        int boundary = 0;
        while (boundary < value.length()
                && (Character.isLetterOrDigit(value.charAt(boundary)) || value.charAt(boundary) == '-')) {
            boundary++;
        }
        String name = value.substring(0, boundary).toLowerCase(java.util.Locale.ROOT);
        if (value.matches("(?:(?:\\d+):)?\\d{2}:\\d{2}[.,]\\d{3}")) return true;
        return name.equals("b") || name.equals("i") || name.equals("u") || name.equals("c")
                || name.equals("v") || name.equals("lang") || name.equals("ruby") || name.equals("rt");
    }

    public record Result(String text, boolean strippedMarkup) { }
}
