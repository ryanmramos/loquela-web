package com.example.whatsanki.service.util;

import com.example.whatsanki.dto.AnkiCardFieldsDto;
import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionarySenseDto;
import com.example.whatsanki.dto.NumberedContextExampleDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps a saved lookup snapshot to the versioned, exporter-ready Anki field contract. */
@Component
public class AnkiCardFieldMapper {

    private static final Pattern WORD_BOUNDARY_TEMPLATE = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])%s(?![\\p{L}\\p{N}])");

    public AnkiCardFieldsDto map(DictionaryEntryDto entry, NumberedContextExampleDto context) {
        ContextSplit split = splitContext(entry, context.displayText());
        DictionarySenseDto primarySense = entry.senses().get(0);

        return new AnkiCardFieldsDto(
                AnkiCardFieldsDto.CURRENT_SCHEMA_VERSION,
                AnkiCardFieldsDto.NOTE_TYPE_NAME,
                AnkiCardFieldsDto.TEMPLATE_VERSION,
                entry.requestedTerm(),
                split.before(),
                split.target(),
                split.after(),
                "", // Translation is deferred; source context must remain provider/source-authored text.
                primarySense.definition(),
                remainingDefinitions(entry.senses()),
                "", // Dictionary-provider metadata can populate pronunciation/grammar in later adapters.
                primarySense.partOfSpeech(),
                "",
                "",
                wordFamily(entry),
                context.sourceDisplayName(),
                sourceLocator(context.location()),
                entry.languagePair().sourceLanguage() + " → " + entry.languagePair().definitionLanguage(),
                "", "", "",
                "1", // Current PDF/text cards deliberately generate the text-recognition template only.
                "", ""
        );
    }

    /**
     * The templates concatenate the three context fields. Split the original plain
     * text here, before export, so highlighting never depends on unsafe source HTML
     * and the exact encountered surface form remains visible on every Anki client.
     */
    private ContextSplit splitContext(DictionaryEntryDto entry, String text) {
        for (String candidate : targetCandidates(entry)) {
            Pattern targetPattern = Pattern.compile(
                    WORD_BOUNDARY_TEMPLATE.pattern().formatted(Pattern.quote(candidate)),
                    WORD_BOUNDARY_TEMPLATE.flags()
            );
            Matcher matcher = targetPattern.matcher(text);
            if (matcher.find()) {
                return new ContextSplit(text.substring(0, matcher.start()), matcher.group(), text.substring(matcher.end()));
            }
        }
        throw new IllegalArgumentException("Selected context does not contain the requested term or a known form");
    }

    private List<String> targetCandidates(DictionaryEntryDto entry) {
        List<String> values = new ArrayList<>(entry.searchTerms());
        values.sort(Comparator.comparingInt(String::length).reversed());
        return values.stream().distinct().toList();
    }

    private String remainingDefinitions(List<DictionarySenseDto> senses) {
        return senses.stream()
                .skip(1)
                .map(sense -> sense.partOfSpeech() + ": " + sense.definition())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String wordFamily(DictionaryEntryDto entry) {
        List<String> forms = entry.forms().stream()
                .filter(form -> !form.equalsIgnoreCase(entry.requestedTerm()))
                .toList();
        if (forms.isEmpty() && entry.lemma().equalsIgnoreCase(entry.requestedTerm())) {
            return "";
        }
        List<String> values = new ArrayList<>();
        if (!entry.lemma().equalsIgnoreCase(entry.requestedTerm())) {
            values.add("Lemma: " + entry.lemma());
        }
        if (!forms.isEmpty()) {
            values.add("Forms: " + String.join(", ", forms));
        }
        return String.join(" · ", values);
    }

    private String sourceLocator(Map<String, Object> location) {
        Object page = location.get("pageNumber");
        if (page instanceof Number number && number.intValue() > 0) {
            return "p. " + number.intValue();
        }
        Object startMs = location.get("startMs");
        if (startMs instanceof Number number && number.longValue() >= 0) {
            return timestamp(number.longValue());
        }
        return "";
    }

    private String timestamp(long milliseconds) {
        long seconds = milliseconds / 1_000;
        long hours = seconds / 3_600;
        long minutes = (seconds % 3_600) / 60;
        long remainingSeconds = seconds % 60;
        return hours > 0
                ? "%d:%02d:%02d".formatted(hours, minutes, remainingSeconds)
                : "%d:%02d".formatted(minutes, remainingSeconds);
    }

    private record ContextSplit(String before, String target, String after) { }
}
