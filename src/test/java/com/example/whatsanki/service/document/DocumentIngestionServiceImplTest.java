package com.example.whatsanki.service.document;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DetectedDocumentTypeDto;
import com.example.whatsanki.dto.DownloadedMediaDto;
import com.example.whatsanki.dto.FetchMediaJobPayloadDto;
import com.example.whatsanki.dto.MediaFetchPreparationDto;
import com.example.whatsanki.dto.StoredObjectMetadataDto;
import com.example.whatsanki.dto.TwilioMediaMetadataDto;
import com.example.whatsanki.dto.TwilioMediaReferenceDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.SensitiveDataEncryptionService;
import com.example.whatsanki.service.exception.MediaAcquisitionException;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.service.storage.ObjectStore;
import com.example.whatsanki.service.twilio.TwilioMediaDownloader;
import com.example.whatsanki.service.util.DocumentMediaTypeDetector;
import com.example.whatsanki.service.util.ObjectKeyFactory;
import com.example.whatsanki.service.util.TextNormalizer;
import com.example.whatsanki.service.util.TwilioMediaUrlPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceImplTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private InboundMessageRepository inboundMessageRepository;
    @Mock private SourceDocumentRepository sourceDocumentRepository;
    @Mock private BackgroundJobService backgroundJobService;
    @Mock private SensitiveDataEncryptionService encryptionService;
    @Mock private TwilioMediaUrlPolicy mediaUrlPolicy;
    @Mock private TwilioMediaDownloader mediaDownloader;
    @Mock private DocumentMediaTypeDetector mediaTypeDetector;
    @Mock private ObjectStore objectStore;
    @Mock private DocumentMediaLifecycleService lifecycleService;
    @Mock private TextNormalizer textNormalizer;
    @TempDir Path temporaryDirectory;

    private DocumentIngestionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentIngestionServiceImpl(
                appUserRepository, inboundMessageRepository, sourceDocumentRepository,
                backgroundJobService, encryptionService, mediaUrlPolicy, mediaDownloader,
                mediaTypeDetector, objectStore, new ObjectKeyFactory(), lifecycleService,
                textNormalizer
        );
    }

    @Test
    void transactionallyEncryptsMediaReferenceCreatesSourceAndEnqueuesIdOnlyJob() {
        UserId userId = UserId.random();
        InboundMessageId inboundId = InboundMessageId.random();
        String messageSid = "MM11111111111111111111111111111111";
        String mediaSid = "ME22222222222222222222222222222222";
        URI mediaUri = URI.create("https://api.twilio.com/2010-04-01/Accounts/AC/Messages/"
                + messageSid + "/Media/" + mediaSid);
        InboundMessageEntity inbound = new InboundMessageEntity();
        inbound.setId(inboundId.value());
        inbound.setUserId(userId.value());
        inbound.setCommandType(InboundMessageEntity.CommandType.DOCUMENT);
        inbound.setTwilioMessageSid(messageSid);
        AppUserEntity user = new AppUserEntity();
        user.setId(userId.value());
        user.setStatus(AppUserEntity.Status.ACTIVE);
        user.setSourceLanguage("es");
        when(sourceDocumentRepository.findByUserIdAndInboundMessageId(userId.value(), inboundId.value()))
                .thenReturn(Optional.empty());
        when(inboundMessageRepository.findByIdAndUserId(inboundId.value(), userId.value()))
                .thenReturn(Optional.of(inbound));
        when(appUserRepository.findById(userId.value())).thenReturn(Optional.of(user));
        when(encryptionService.encrypt(eq(mediaUri.toString()), any())).thenReturn(new byte[]{1, 2});
        when(mediaUrlPolicy.reference(mediaUri)).thenReturn(new TwilioMediaReferenceDto(messageSid, mediaSid));
        when(textNormalizer.normalizeDisplay("My book.pdf")).thenReturn("My book.pdf");
        when(backgroundJobService.enqueue(eq(userId), eq(BackgroundJobEntity.Type.FETCH_MEDIA), any()))
                .thenReturn(JobId.random());

        var accepted = service.accept(userId, inboundId,
                new TwilioMediaMetadataDto(1, mediaUri.toString(), "application/pdf", "My book.pdf"));

        assertThat(inbound.getMediaUrlCiphertext()).containsExactly(1, 2);
        assertThat(inbound.getMediaContentType()).isEqualTo("application/pdf");
        assertThat(inbound.getTwilioMediaSid()).isEqualTo(mediaSid);
        ArgumentCaptor<SourceDocumentEntity> source = ArgumentCaptor.forClass(SourceDocumentEntity.class);
        verify(sourceDocumentRepository).save(source.capture());
        assertThat(source.getValue().getId()).isEqualTo(accepted.sourceId().value());
        assertThat(source.getValue().getKind()).isEqualTo(SourceDocumentEntity.Kind.PDF);
        assertThat(source.getValue().getStatus()).isEqualTo(SourceDocumentEntity.Status.RECEIVED);
        assertThat(source.getValue().getDisplayName()).isEqualTo("My book.pdf");
        assertThat(source.getValue().getLanguage()).isEqualTo("es");
        verify(backgroundJobService).enqueue(
                userId, BackgroundJobEntity.Type.FETCH_MEDIA,
                FetchMediaJobPayloadDto.create(accepted.sourceId()).toMap()
        );
    }

    @Test
    void rejectsTxtAndOtherDeferredFormatsBeforePersistingStaging() {
        UserId userId = UserId.random();
        InboundMessageId inboundId = InboundMessageId.random();
        String messageSid = "MM11111111111111111111111111111111";
        String mediaSid = "ME22222222222222222222222222222222";
        URI mediaUri = URI.create("https://api.twilio.com/2010-04-01/Accounts/AC/Messages/"
                + messageSid + "/Media/" + mediaSid);
        when(mediaUrlPolicy.reference(mediaUri)).thenReturn(new TwilioMediaReferenceDto(messageSid, mediaSid));

        assertThatThrownBy(() -> service.accept(
                userId,
                inboundId,
                new TwilioMediaMetadataDto(1, mediaUri.toString(), "text/plain", "notes.txt")
        )).isInstanceOfSatisfying(
                MediaAcquisitionException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE")
        );

        verify(inboundMessageRepository, never()).save(any());
        verify(sourceDocumentRepository, never()).save(any());
        verify(backgroundJobService, never()).enqueue(any(), any(), any());
    }

    @Test
    void downloadsDetectsAndStoresAtDeterministicKeyOutsideLifecycleTransactions() throws Exception {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        URI mediaUri = URI.create("https://api.twilio.com/media");
        Path file = temporaryDirectory.resolve("download");
        Files.writeString(file, "hola");
        DownloadedMediaDto downloaded = new DownloadedMediaDto(file, 4, new byte[32]);
        var detected = new DetectedDocumentTypeDto(SourceDocumentEntity.Kind.PDF, "application/pdf");
        when(lifecycleService.prepare(userId, sourceId))
                .thenReturn(new MediaFetchPreparationDto(false, mediaUri, "application/pdf"));
        when(mediaDownloader.download(mediaUri)).thenReturn(downloaded);
        when(mediaTypeDetector.detect(file, "application/pdf")).thenReturn(detected);
        when(lifecycleService.complete(eq(userId), eq(sourceId), any(), eq(detected), any()))
                .thenReturn(DocumentMediaLifecycleService.CompletionOutcome.STORED);

        service.fetchAndStore(userId, sourceId);

        String key = "users/" + userId.value() + "/sources/" + sourceId.value() + "/original";
        ArgumentCaptor<StoredObjectMetadataDto> metadata = ArgumentCaptor.forClass(StoredObjectMetadataDto.class);
        verify(objectStore).put(eq(key), eq(file), metadata.capture());
        assertThat(metadata.getValue().contentLength()).isEqualTo(4);
        assertThat(file).doesNotExist();
    }

    @Test
    void delegatesFailureTransitionWithoutPerformingInlineObjectCleanup() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        service.markFailed(userId, sourceId, "MEDIA_DOWNLOAD_FAILED", true);

        verify(lifecycleService).markFailed(userId, sourceId, "MEDIA_DOWNLOAD_FAILED", true);
        verify(objectStore, never()).delete(any());
    }
}
