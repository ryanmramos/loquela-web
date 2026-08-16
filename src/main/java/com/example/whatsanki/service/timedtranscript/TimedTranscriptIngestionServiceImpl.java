package com.example.whatsanki.service.timedtranscript;

import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.TimedCaptionCueDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.ObjectStoreException;
import com.example.whatsanki.service.exception.TimedTranscriptException;
import com.example.whatsanki.service.storage.ObjectStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TimedTranscriptIngestionServiceImpl implements TimedTranscriptIngestionService {
    private final List<TimedTranscriptParser> parserList;
    private final TimedTranscriptLifecycleService lifecycle;
    private final RollingCaptionNormalizer normalizer;
    private final TimedTranscriptPassageBuilder passageBuilder;
    private final ObjectStore objectStore;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    public void ingest(UserId userId, SourceId sourceId) {
        TimedTranscriptLifecycleService.Preparation preparation = lifecycle.prepare(userId, sourceId);
        if (preparation.objectKey() == null) return;
        if (!preparation.ready()) {
            String text = decode(readBounded(preparation.objectKey()));
            TimedTranscriptParseResult parsed = parsers().get(preparation.format()).parse(text,
                    configuration.getTimedTranscript().getMaxCues(),
                    configuration.getTimedTranscript().getMaxNormalizedCharacters(),
                    configuration.getTimedTranscript().getMaxTimeline().toMillis());
            lifecycle.storeParsedMetadata(userId, sourceId, parsed);
            RollingCaptionNormalizer.Result normalized = normalizer.normalize(parsed.cues());
            lifecycle.markIndexing(userId, sourceId, normalized.coalescedCueCount());
            long characters = normalized.cues().stream().mapToLong(cue -> cue.text().codePointCount(0, cue.text().length())).sum();
            long timelineEnd = parsed.cues().stream().mapToLong(TimedCaptionCueDto::endMs).max().orElse(0);
            lifecycle.complete(userId, sourceId,
                    passageBuilder.build(normalized.cues(), preparation.origin(), preparation.timelineOffsetMs()),
                    characters, timelineEnd);
        }
        deleteAndClear(userId, sourceId, preparation.objectKey());
    }

    @Override
    public void markFailedAndDelete(UserId userId, SourceId sourceId, String errorCode) {
        TimedTranscriptLifecycleService.Preparation preparation;
        try { preparation = lifecycle.prepare(userId, sourceId); }
        catch (RuntimeException ignored) { lifecycle.markFailed(userId, sourceId, errorCode); return; }
        lifecycle.markFailed(userId, sourceId, errorCode);
        if (preparation.objectKey() != null) deleteAndClear(userId, sourceId, preparation.objectKey());
    }

    private byte[] readBounded(String objectKey) {
        long maximum = configuration.getTimedTranscript().getMaxFileBytes();
        try (InputStream input = objectStore.open(objectKey);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(maximum, 64 * 1024))) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            for (int read; (read = input.read(buffer)) != -1;) {
                total += read;
                if (total > maximum) throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_TOO_LARGE");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (TimedTranscriptException exception) { throw exception; }
        catch (IOException | ObjectStoreException exception) {
            throw TimedTranscriptException.transientFailure("TIMED_TRANSCRIPT_PROCESSING_FAILED", exception);
        }
    }

    private String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw TimedTranscriptException.permanent("TIMED_TRANSCRIPT_INVALID_UTF8");
        }
    }

    private Map<TimedTranscriptFormat, TimedTranscriptParser> parsers() {
        Map<TimedTranscriptFormat, TimedTranscriptParser> parsers = new EnumMap<>(TimedTranscriptFormat.class);
        for (TimedTranscriptParser parser : parserList) parsers.put(parser.format(), parser);
        if (parsers.size() != TimedTranscriptFormat.values().length) {
            throw new IllegalStateException("Every timed transcript format must have exactly one parser");
        }
        return parsers;
    }

    private void deleteAndClear(UserId userId, SourceId sourceId, String objectKey) {
        try { objectStore.delete(objectKey); lifecycle.clearTemporaryObjectKey(userId, sourceId); }
        catch (ObjectStoreException exception) {
            throw TimedTranscriptException.transientFailure("TIMED_TRANSCRIPT_PROCESSING_FAILED", exception);
        }
    }
}
