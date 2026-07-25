package com.example.whatsanki.service.youtube;

import com.example.whatsanki.dto.TimedCaptionCueDto;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebVttCaptionParser {
    private static final Pattern TIMING = Pattern.compile(
            "(?:(\\d+):)?(\\d{2}):(\\d{2})[.,](\\d{3})\\s+-->\\s+(?:(\\d+):)?(\\d{2}):(\\d{2})[.,](\\d{3}).*"
    );
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    public List<TimedCaptionCueDto> parse(String vtt, int maximumCues) {
        if (vtt == null || !vtt.stripLeading().startsWith("WEBVTT")) {
            throw new IllegalArgumentException("Malformed WebVTT document");
        }
        String[] lines = vtt.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<TimedCaptionCueDto> cues = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            Matcher timing = TIMING.matcher(lines[index].strip());
            if (!timing.matches()) continue;
            long start = milliseconds(timing, 1);
            long end = milliseconds(timing, 5);
            StringBuilder text = new StringBuilder();
            while (++index < lines.length && !lines[index].isBlank()) {
                if (!text.isEmpty()) text.append(' ');
                text.append(lines[index]);
            }
            String cleaned = HtmlUtils.htmlUnescape(TAG.matcher(text).replaceAll(""))
                    .replaceAll("[\\p{Z}\\s]+", " ").strip();
            if (!cleaned.isBlank() && end > start) {
                mergeRollingCue(cues, new TimedCaptionCueDto(start, end, cleaned));
                if (cues.size() > maximumCues) {
                    throw new IllegalArgumentException("WebVTT cue limit exceeded");
                }
            }
        }
        if (cues.isEmpty()) throw new IllegalArgumentException("WebVTT contains no readable cues");
        return List.copyOf(cues);
    }

    private void mergeRollingCue(List<TimedCaptionCueDto> cues, TimedCaptionCueDto current) {
        if (cues.isEmpty()) {
            cues.add(current);
            return;
        }
        TimedCaptionCueDto previous = cues.get(cues.size() - 1);
        if (current.startMs() <= previous.endMs() + 2_000
                && (current.text().equals(previous.text()) || current.text().startsWith(previous.text() + " "))) {
            cues.set(cues.size() - 1, new TimedCaptionCueDto(
                    previous.startMs(), Math.max(previous.endMs(), current.endMs()), current.text()
            ));
        } else {
            cues.add(current);
        }
    }

    private long milliseconds(Matcher matcher, int offset) {
        long hours = matcher.group(offset) == null ? 0 : Long.parseLong(matcher.group(offset));
        return hours * 3_600_000L
                + Long.parseLong(matcher.group(offset + 1)) * 60_000L
                + Long.parseLong(matcher.group(offset + 2)) * 1_000L
                + Long.parseLong(matcher.group(offset + 3));
    }
}
