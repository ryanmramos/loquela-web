package com.example.whatsanki.service.util;

import com.example.whatsanki.domain.SourceDocumentEntity;
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

/** Maps lookup snapshots to the exact ordered field contract consumed by the exporter. */
@Component
public class AnkiCardFieldMapper {

    private static final Pattern WORD_BOUNDARY_TEMPLATE = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])%s(?![\\p{L}\\p{N}])");
    private static final Pattern YOUTUBE_VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    public AnkiCardFieldsDto map(DictionaryEntryDto entry, NumberedContextExampleDto context) {
        ContextSplit split = splitContext(entry, context.displayText());
        DictionarySenseDto primarySense = entry.senses().get(0);
        boolean videoContext = context.sourceKind() == SourceDocumentEntity.Kind.YOUTUBE_VIDEO
                || (context.sourceKind() == SourceDocumentEntity.Kind.TIMED_TRANSCRIPT
                    && "YOUTUBE".equals(context.location().get("provider")));
        String videoUrl = videoContext ? videoUrl(context.location()) : "";

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
                sourceName(context),
                sourceLocator(context.location()),
                entry.languagePair().sourceLanguage() + " → " + entry.languagePair().definitionLanguage(),
                "", "", videoUrl,
                videoContext ? "" : "1",
                videoContext ? "1" : "", ""
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
        Object startMs = location.getOrDefault("originTimestampMs", location.get("startMs"));
        if (startMs instanceof Number number && number.longValue() >= 0) {
            return timestamp(number.longValue());
        }
        return "";
    }

    private String sourceName(NumberedContextExampleDto context) {
        if (context.sourceKind() != SourceDocumentEntity.Kind.YOUTUBE_VIDEO
                && !(context.sourceKind() == SourceDocumentEntity.Kind.TIMED_TRANSCRIPT
                    && "YOUTUBE".equals(context.location().get("provider")))) {
            return context.sourceDisplayName();
        }
        Object channelName = context.location().get("channelName");
        return channelName instanceof String channel && !channel.isBlank()
                ? "YouTube · " + channel.strip()
                : "YouTube · " + context.sourceDisplayName();
    }

    private String videoUrl(Map<String, Object> location) {
        Object videoIdValue = location.get("videoId");
        if (!(videoIdValue instanceof String videoId) || !YOUTUBE_VIDEO_ID.matcher(videoId).matches()) {
            throw new IllegalArgumentException("YouTube context has an invalid video id");
        }
        long playbackStartMs = nonNegativeLong(location.get("playbackStartMs"),
                nonNegativeLong(location.get("startMs"), -1));
        if (playbackStartMs < 0) {
            throw new IllegalArgumentException("YouTube context has no playback timestamp");
        }
        // Only this controlled URL shape crosses the exporter HTML boundary. The
        // exporter independently validates it before Anki renders it in an iframe.
        return "https://www.youtube-nocookie.com/embed/%s?start=%d&rel=0"
                .formatted(videoId, playbackStartMs / 1_000);
    }

    private long nonNegativeLong(Object value, long fallback) {
        return value instanceof Number number && number.longValue() >= 0
                ? number.longValue()
                : fallback;
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
