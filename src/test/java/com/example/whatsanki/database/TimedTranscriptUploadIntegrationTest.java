package com.example.whatsanki.database;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.CreateTimedTranscriptUploadRequestDto;
import com.example.whatsanki.dto.AcceptedSourceDto;
import com.example.whatsanki.dto.StoredObjectMetadataDto;
import com.example.whatsanki.dto.StoredObjectReferenceDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.PassageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.repository.SourceUploadRepository;
import com.example.whatsanki.repository.TimedTranscriptMetadataRepository;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.exception.BrowserSourceValidationException;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.service.storage.ObjectStore;
import com.example.whatsanki.service.timedtranscript.OriginUrlPolicy;
import com.example.whatsanki.service.timedtranscript.RollingCaptionNormalizer;
import com.example.whatsanki.service.timedtranscript.TimedTranscriptIngestionServiceImpl;
import com.example.whatsanki.service.timedtranscript.TimedTranscriptLifecycleService;
import com.example.whatsanki.service.timedtranscript.TimedTranscriptParser;
import com.example.whatsanki.service.timedtranscript.TimedTranscriptPassageBuilder;
import com.example.whatsanki.service.timedtranscript.TimedTranscriptUploadServiceImpl;
import com.example.whatsanki.service.util.ObjectKeyFactory;
import com.example.whatsanki.service.util.SourceReferenceCodec;
import com.example.whatsanki.service.util.TextNormalizer;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimedTranscriptUploadIntegrationTest extends PostgreSqlIntegrationTest {
    private final AppUserRepository userRepository;
    private final SourceUploadRepository uploadRepository;
    private final SourceDocumentRepository sourceRepository;
    private final TimedTranscriptMetadataRepository metadataRepository;
    private final PassageRepository passageRepository;
    private final ObjectKeyFactory objectKeyFactory;
    private final TextNormalizer textNormalizer;
    private final OriginUrlPolicy originUrlPolicy;
    private final TimedTranscriptLifecycleService lifecycle;
    private final List<TimedTranscriptParser> parsers;
    private final RollingCaptionNormalizer normalizer;
    private final TimedTranscriptPassageBuilder passageBuilder;
    private final WhatsAnkiConfigurationDto configuration;
    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbcTemplate;

    TimedTranscriptUploadIntegrationTest(
            AppUserRepository userRepository, SourceUploadRepository uploadRepository,
            SourceDocumentRepository sourceRepository, TimedTranscriptMetadataRepository metadataRepository,
            PassageRepository passageRepository,
            ObjectKeyFactory objectKeyFactory, TextNormalizer textNormalizer, OriginUrlPolicy originUrlPolicy,
            TimedTranscriptLifecycleService lifecycle, List<TimedTranscriptParser> parsers,
            RollingCaptionNormalizer normalizer, TimedTranscriptPassageBuilder passageBuilder,
            WhatsAnkiConfigurationDto configuration, PlatformTransactionManager transactionManager,
            JdbcTemplate jdbcTemplate
    ) {
        this.userRepository = userRepository;
        this.uploadRepository = uploadRepository;
        this.sourceRepository = sourceRepository;
        this.metadataRepository = metadataRepository;
        this.passageRepository = passageRepository;
        this.objectKeyFactory = objectKeyFactory;
        this.textNormalizer = textNormalizer;
        this.originUrlPolicy = originUrlPolicy;
        this.lifecycle = lifecycle;
        this.parsers = parsers;
        this.normalizer = normalizer;
        this.passageBuilder = passageBuilder;
        this.configuration = configuration;
        this.transactions = new TransactionTemplate(transactionManager);
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void vttAndSrtUploadsFlowThroughIntentAndJobProcessingThenDeleteRawObjects() throws Exception {
        configuration.getTimedTranscript().setEnabled(true);
        UserId owner = user();
        UserIdentityService identities = mock(UserIdentityService.class);
        when(identities.findUserId(UserIdentityEntity.Provider.GOOGLE, "subject"))
                .thenReturn(Optional.of(owner));
        BackgroundJobService jobs = mock(BackgroundJobService.class);
        when(jobs.enqueue(any(), any(), any())).thenReturn(JobId.random());
        InMemoryObjectStore objects = new InMemoryObjectStore();
        TimedTranscriptUploadServiceImpl uploads = new TimedTranscriptUploadServiceImpl(
                identities, uploadRepository, sourceRepository, metadataRepository, objects,
                objectKeyFactory, jobs, textNormalizer, originUrlPolicy, configuration);
        TimedTranscriptIngestionServiceImpl ingestion = new TimedTranscriptIngestionServiceImpl(
                parsers, lifecycle, normalizer, passageBuilder, objects, configuration);

        SourceId vtt = uploadAndProcess(uploads, ingestion, owner, "lesson.es.vtt", "text/vtt", """
                WEBVTT

                00:00:01.000 --> 00:00:03.000
                Quiero aprender español.
                """);
        SourceId srt = uploadAndProcess(uploads, ingestion, owner, "lesson.srt", "application/x-subrip", """
                1
                00:00:01,000 --> 00:00:03,000
                Quiero aprender español.
                """);

        assertThat(passageRepository.findAllByUserIdAndSourceIdOrderByOrdinal(owner.value(), vtt.value()))
                .extracting("displayText").containsExactly("Quiero aprender español.");
        assertThat(sourceRepository.findByIdAndUserId(vtt.value(), owner.value()).orElseThrow().getDisplayName())
                .isEqualTo("lesson");
        assertThat(passageRepository.findAllByUserIdAndSourceIdOrderByOrdinal(owner.value(), srt.value()))
                .extracting("displayText").containsExactly("Quiero aprender español.");
        assertThat(sourceRepository.storedBytesByIdAndUserId(vtt.value(), owner.value()))
                .isEqualTo(sourceRowBytes(vtt, owner) + passageRowBytes(vtt, owner)
                        + metadataRowBytes(vtt, owner) + uploadRowBytes(vtt, owner));
        assertThat(objects.keys()).isEmpty();

        ArgumentCaptor<Map<String, Object>> payloads = ArgumentCaptor.forClass(Map.class);
        verify(jobs, times(2)).enqueue(any(), eq(BackgroundJobEntity.Type.INGEST_TIMED_TRANSCRIPT), payloads.capture());
        assertThat(payloads.getAllValues()).allSatisfy(payload -> {
            assertThat(payload).containsEntry("schemaVersion", 1);
            assertThat(payload).containsKey("sourceId");
        });
    }

    @Test
    void invalidOriginIsRejectedAsAClientValidationError() throws Exception {
        configuration.getTimedTranscript().setEnabled(true);
        UserId owner = user();
        UserIdentityService identities = mock(UserIdentityService.class);
        when(identities.findUserId(UserIdentityEntity.Provider.GOOGLE, "invalid-origin-subject"))
                .thenReturn(Optional.of(owner));
        TimedTranscriptUploadServiceImpl uploads = new TimedTranscriptUploadServiceImpl(
                identities, uploadRepository, sourceRepository, metadataRepository, new InMemoryObjectStore(),
                objectKeyFactory, mock(BackgroundJobService.class), textNormalizer, originUrlPolicy, configuration);

        assertThatThrownBy(() -> uploads.createIntent("invalid-origin-subject", new CreateTimedTranscriptUploadRequestDto(
                "lesson.vtt", "text/vtt", 1, "0".repeat(64), "es", "http://media.example/lesson", 0)))
                .isInstanceOfSatisfying(BrowserSourceValidationException.class, exception -> {
                    assertThat(exception.problemCode()).isEqualTo("TIMED_TRANSCRIPT_ORIGIN_INVALID");
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void transactionRollbackDeletesAnObjectThatNeverReceivedCommittedMetadata() throws Exception {
        configuration.getTimedTranscript().setEnabled(true);
        UserId owner = user();
        UserIdentityService identities = mock(UserIdentityService.class);
        when(identities.findUserId(UserIdentityEntity.Provider.GOOGLE, "rollback-subject"))
                .thenReturn(Optional.of(owner));
        BackgroundJobService jobs = mock(BackgroundJobService.class);
        when(jobs.enqueue(any(), any(), any())).thenReturn(JobId.random());
        InMemoryObjectStore objects = new InMemoryObjectStore();
        TimedTranscriptUploadServiceImpl uploads = new TimedTranscriptUploadServiceImpl(
                identities, uploadRepository, sourceRepository, metadataRepository, objects,
                objectKeyFactory, jobs, textNormalizer, originUrlPolicy, configuration);
        byte[] bytes = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHola.\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        var intent = uploads.createIntent("rollback-subject", new CreateTimedTranscriptUploadRequestDto(
                "rollback.vtt", "text/vtt", bytes.length, checksum, "es", null, 0));

        AcceptedSourceDto accepted = transactions.execute(status -> {
            AcceptedSourceDto value = uploads.complete("rollback-subject", intent.uploadReference(),
                    new MockMultipartFile("file", "rollback.vtt", "text/vtt", bytes));
            status.setRollbackOnly();
            return value;
        });

        SourceId rolledBackSource = SourceReferenceCodec.decode(accepted.reference()).orElseThrow();
        assertThat(sourceRepository.findByIdAndUserId(rolledBackSource.value(), owner.value())).isEmpty();
        assertThat(objects.keys()).isEmpty();
    }

    @Test
    void terminalParseFailureMarksSourceFailedAndDeletesRawObject() throws Exception {
        configuration.getTimedTranscript().setEnabled(true);
        UserId owner = user();
        UserIdentityService identities = mock(UserIdentityService.class);
        when(identities.findUserId(UserIdentityEntity.Provider.GOOGLE, "failure-subject"))
                .thenReturn(Optional.of(owner));
        BackgroundJobService jobs = mock(BackgroundJobService.class);
        when(jobs.enqueue(any(), any(), any())).thenReturn(JobId.random());
        InMemoryObjectStore objects = new InMemoryObjectStore();
        TimedTranscriptUploadServiceImpl uploads = new TimedTranscriptUploadServiceImpl(
                identities, uploadRepository, sourceRepository, metadataRepository, objects,
                objectKeyFactory, jobs, textNormalizer, originUrlPolicy, configuration);
        TimedTranscriptIngestionServiceImpl ingestion = new TimedTranscriptIngestionServiceImpl(
                parsers, lifecycle, normalizer, passageBuilder, objects, configuration);
        byte[] bytes = "not a valid webvtt file".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        var intent = uploads.createIntent("failure-subject", new CreateTimedTranscriptUploadRequestDto(
                "broken.vtt", "text/vtt", bytes.length, checksum, "es", null, 0));
        AcceptedSourceDto accepted = transactions.execute(status -> uploads.complete("failure-subject",
                intent.uploadReference(), new MockMultipartFile("file", "broken.vtt", "text/vtt", bytes)));
        SourceId sourceId = SourceReferenceCodec.decode(accepted.reference()).orElseThrow();

        assertThatThrownBy(() -> ingestion.ingest(owner, sourceId))
                .isInstanceOf(com.example.whatsanki.service.exception.TimedTranscriptException.class);
        ingestion.markFailedAndDelete(owner, sourceId, "TIMED_TRANSCRIPT_MALFORMED");

        assertThat(sourceRepository.findByIdAndUserId(sourceId.value(), owner.value()).orElseThrow().getStatus())
                .isEqualTo(com.example.whatsanki.domain.SourceDocumentEntity.Status.FAILED);
        assertThat(objects.keys()).isEmpty();
    }

    private SourceId uploadAndProcess(TimedTranscriptUploadServiceImpl uploads,
                                      TimedTranscriptIngestionServiceImpl ingestion, UserId owner,
                                      String filename, String mediaType, String text) throws Exception {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        var intent = uploads.createIntent("subject", new CreateTimedTranscriptUploadRequestDto(
                filename, mediaType, bytes.length, checksum, "es", "https://media.example/lesson", 0));
        AcceptedSourceDto accepted = transactions.execute(status -> uploads.complete("subject", intent.uploadReference(),
                new MockMultipartFile("file", filename, mediaType, bytes)));
        SourceId sourceId = SourceReferenceCodec.decode(accepted.reference()).orElseThrow();

        ingestion.ingest(owner, sourceId);

        assertThat(sourceRepository.findByIdAndUserId(sourceId.value(), owner.value()).orElseThrow().getStatus())
                .isEqualTo(com.example.whatsanki.domain.SourceDocumentEntity.Status.READY);
        assertThat(metadataRepository.findBySourceIdAndUserId(sourceId.value(), owner.value()).orElseThrow()
                .getTemporaryObjectKey()).isNull();
        return sourceId;
    }

    private UserId user() {
        AppUserEntity user = new AppUserEntity();
        user.setSourceLanguage("es");
        user.setDefinitionLanguage("en");
        user.setStatus(AppUserEntity.Status.ACTIVE);
        return new UserId(userRepository.saveAndFlush(user).getId());
    }

    private long sourceRowBytes(SourceId sourceId, UserId owner) {
        return jdbcTemplate.queryForObject("""
                select pg_column_size(source)
                from source_document source
                where source.id = ? and source.user_id = ?
                """, Long.class, sourceId.value(), owner.value());
    }

    private long passageRowBytes(SourceId sourceId, UserId owner) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(pg_column_size(passage)), 0)
                from passage
                where source_id = ? and user_id = ?
                """, Long.class, sourceId.value(), owner.value());
    }

    private long metadataRowBytes(SourceId sourceId, UserId owner) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(pg_column_size(metadata)), 0)
                from timed_transcript_metadata metadata
                where source_id = ? and user_id = ?
                """, Long.class, sourceId.value(), owner.value());
    }

    private long uploadRowBytes(SourceId sourceId, UserId owner) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(pg_column_size(upload)), 0)
                from source_upload upload
                where source_id = ? and user_id = ?
                """, Long.class, sourceId.value(), owner.value());
    }

    private static final class InMemoryObjectStore implements ObjectStore {
        private final Map<String, byte[]> values = new ConcurrentHashMap<>();

        @Override public void put(String objectKey, Path source, StoredObjectMetadataDto metadata) {
            try { values.put(objectKey, Files.readAllBytes(source)); }
            catch (IOException exception) { throw new IllegalStateException(exception); }
        }
        @Override public InputStream open(String objectKey) {
            return new ByteArrayInputStream(values.get(objectKey));
        }
        @Override public void delete(String objectKey) { values.remove(objectKey); }
        @Override public URI createPresignedGetUrl(String objectKey, Duration lifetime) {
            throw new UnsupportedOperationException();
        }
        @Override public List<StoredObjectReferenceDto> list(String prefix, int maximum) {
            return List.of();
        }
        java.util.Set<String> keys() { return values.keySet(); }
    }
}
