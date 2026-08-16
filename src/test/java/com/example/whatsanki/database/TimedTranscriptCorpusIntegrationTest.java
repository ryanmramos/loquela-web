package com.example.whatsanki.database;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.TimedTranscriptMetadataEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.CorpusSearchOptionsDto;
import com.example.whatsanki.dto.TimedCaptionCueDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.repository.TimedTranscriptMetadataRepository;
import com.example.whatsanki.service.lookup.CorpusSearch;
import com.example.whatsanki.service.timedtranscript.OriginUrlPolicy;
import com.example.whatsanki.service.timedtranscript.RollingCaptionNormalizer;
import com.example.whatsanki.service.timedtranscript.TimedTranscriptLifecycleService;
import com.example.whatsanki.service.timedtranscript.TimedTranscriptParseResult;
import com.example.whatsanki.service.timedtranscript.TimedTranscriptPassageBuilder;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TimedTranscriptCorpusIntegrationTest extends PostgreSqlIntegrationTest {
    private final AppUserRepository userRepository;
    private final SourceDocumentRepository sourceRepository;
    private final TimedTranscriptMetadataRepository metadataRepository;
    private final TimedTranscriptLifecycleService lifecycle;
    private final RollingCaptionNormalizer normalizer;
    private final TimedTranscriptPassageBuilder passageBuilder;
    private final OriginUrlPolicy originUrlPolicy;
    private final CorpusSearch corpusSearch;

    TimedTranscriptCorpusIntegrationTest(
            AppUserRepository userRepository,
            SourceDocumentRepository sourceRepository,
            TimedTranscriptMetadataRepository metadataRepository,
            TimedTranscriptLifecycleService lifecycle,
            RollingCaptionNormalizer normalizer,
            TimedTranscriptPassageBuilder passageBuilder,
            OriginUrlPolicy originUrlPolicy,
            CorpusSearch corpusSearch
    ) {
        this.userRepository = userRepository;
        this.sourceRepository = sourceRepository;
        this.metadataRepository = metadataRepository;
        this.lifecycle = lifecycle;
        this.normalizer = normalizer;
        this.passageBuilder = passageBuilder;
        this.originUrlPolicy = originUrlPolicy;
        this.corpusSearch = corpusSearch;
    }

    @Test
    void timedTranscriptBecomesOwnerScopedTimestampedLookupContext() {
        UserId owner = user();
        UserId otherUser = user();
        SourceId sourceId = storedTranscript(owner);
        OriginUrlPolicy.Origin origin = originUrlPolicy.validate("https://media.example/lesson/42");
        TimedTranscriptParseResult parsed = new TimedTranscriptParseResult(
                List.of(new TimedCaptionCueDto(0, 8_000, 12_000, "Quiero vivir aquí.")),
                0, 0, 0
        );

        assertThat(lifecycle.prepare(owner, sourceId).ready()).isFalse();
        lifecycle.storeParsedMetadata(owner, sourceId, parsed);
        RollingCaptionNormalizer.Result normalized = normalizer.normalize(parsed.cues());
        lifecycle.markIndexing(owner, sourceId, normalized.coalescedCueCount());
        lifecycle.complete(owner, sourceId,
                passageBuilder.build(normalized.cues(), origin, 2_000),
                "Quiero vivir aquí.".codePointCount(0, "Quiero vivir aquí.".length()),
                12_000);

        SourceDocumentEntity source = sourceRepository
                .findByIdAndUserId(sourceId.value(), owner.value()).orElseThrow();
        assertThat(source.getStatus()).isEqualTo(SourceDocumentEntity.Status.READY);
        assertThat(source.getKind()).isEqualTo(SourceDocumentEntity.Kind.TIMED_TRANSCRIPT);
        assertThat(metadataRepository.findBySourceIdAndUserId(sourceId.value(), owner.value()).orElseThrow()
                .getCueCount()).isEqualTo(1);

        var ownerMatches = corpusSearch.findExamples(
                owner, "vivir", new CorpusSearchOptionsDto(new LanguageTag("es"), 5, 5)
        );
        assertThat(ownerMatches).singleElement().satisfies(match -> {
            assertThat(match.sourceKind()).isEqualTo(SourceDocumentEntity.Kind.TIMED_TRANSCRIPT);
            assertThat(match.location())
                    .containsEntry("startMs", 8_000)
                    .containsEntry("originUrl", "https://media.example/lesson/42");
            assertThat(((Number) match.location().get("originTimestampMs")).longValue()).isEqualTo(10_000L);
        });
        assertThat(corpusSearch.findExamples(
                otherUser, "vivir", new CorpusSearchOptionsDto(new LanguageTag("es"), 5, 5)
        )).isEmpty();
    }

    private UserId user() {
        AppUserEntity user = new AppUserEntity();
        user.setSourceLanguage("es");
        user.setDefinitionLanguage("en");
        user.setStatus(AppUserEntity.Status.ACTIVE);
        return new UserId(userRepository.saveAndFlush(user).getId());
    }

    private SourceId storedTranscript(UserId userId) {
        SourceId sourceId = SourceId.random();
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(sourceId.value());
        source.setUserId(userId.value());
        source.setKind(SourceDocumentEntity.Kind.TIMED_TRANSCRIPT);
        source.setDisplayName("lesson.vtt");
        source.setStatus(SourceDocumentEntity.Status.STORED);
        source.setLanguage("es");
        source.setObjectKey("users/fixture/sources/fixture/original");
        source.setOriginalStoredAt(Instant.now());
        source.setMetadata(new LinkedHashMap<>());
        sourceRepository.saveAndFlush(source);

        TimedTranscriptMetadataEntity metadata = new TimedTranscriptMetadataEntity();
        metadata.setSourceId(sourceId.value());
        metadata.setUserId(userId.value());
        metadata.setOriginalFilename("lesson.vtt");
        metadata.setFormat(TimedTranscriptMetadataEntity.Format.VTT);
        metadata.setLanguage("es");
        metadata.setOriginUrl("https://media.example/lesson/42");
        metadata.setTimelineOffsetMs(2_000);
        metadata.setParserVersion("1");
        metadata.setNormalizerVersion(RollingCaptionNormalizer.VERSION);
        metadata.setTemporaryObjectKey(source.getObjectKey());
        metadataRepository.save(metadata);
        return sourceId;
    }
}
