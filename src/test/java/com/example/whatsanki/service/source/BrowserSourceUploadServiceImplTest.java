package com.example.whatsanki.service.source;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceUploadEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.CreateSourceUploadRequestDto;
import com.example.whatsanki.dto.DetectedDocumentTypeDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.repository.SourceUploadRepository;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.exception.BrowserSourceConflictException;
import com.example.whatsanki.service.exception.BrowserSourceValidationException;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.service.storage.ObjectStore;
import com.example.whatsanki.service.util.DocumentMediaTypeDetector;
import com.example.whatsanki.service.util.ObjectKeyFactory;
import com.example.whatsanki.service.util.TextNormalizerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrowserSourceUploadServiceImplTest {
    @Mock private UserIdentityService userIdentityService;
    @Mock private AppUserRepository appUserRepository;
    @Mock private SourceUploadRepository sourceUploadRepository;
    @Mock private SourceDocumentRepository sourceDocumentRepository;
    @Mock private DocumentMediaTypeDetector mediaTypeDetector;
    @Mock private ObjectStore objectStore;
    @Mock private BackgroundJobService backgroundJobService;

    private BrowserSourceUploadServiceImpl service;
    private UserId userId;

    @BeforeEach
    void setUp() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.getIngestion().setMaxDownloadBytes(1024);
        service = new BrowserSourceUploadServiceImpl(
                userIdentityService,
                appUserRepository,
                sourceUploadRepository,
                sourceDocumentRepository,
                mediaTypeDetector,
                objectStore,
                new ObjectKeyFactory(),
                backgroundJobService,
                new TextNormalizerImpl(),
                configuration
        );
        userId = UserId.random();
        when(userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, "subject"))
                .thenReturn(Optional.of(userId));
    }

    @Test
    void finalizesAnIntentOnlyOnceAndQueuesIdOnlyExtraction() throws Exception {
        byte[] content = "hola desde un txt".getBytes(StandardCharsets.UTF_8);
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        ArgumentCaptor<SourceUploadEntity> upload = ArgumentCaptor.forClass(SourceUploadEntity.class);
        when(sourceUploadRepository.save(upload.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        var intent = service.createIntent(
                "subject",
                new CreateSourceUploadRequestDto("notes.txt", "text/plain", content.length, checksum)
        );
        SourceUploadEntity persistedIntent = upload.getValue();
        when(sourceUploadRepository.findForUpdateByIdAndUserId(persistedIntent.getId(), userId.value()))
                .thenReturn(Optional.of(persistedIntent));
        when(mediaTypeDetector.detect(any(), org.mockito.ArgumentMatchers.eq("text/plain")))
                .thenReturn(new DetectedDocumentTypeDto(SourceDocumentEntity.Kind.TXT, "text/plain"));
        AppUserEntity user = new AppUserEntity();
        user.setId(userId.value());
        user.setStatus(AppUserEntity.Status.ACTIVE);
        user.setSourceLanguage("es");
        when(appUserRepository.findById(userId.value())).thenReturn(Optional.of(user));
        when(backgroundJobService.enqueue(any(), any(), any())).thenReturn(JobId.random());

        var accepted = service.complete(
                "subject",
                intent.uploadReference(),
                new MockMultipartFile("file", "notes.txt", "text/plain", content)
        );

        assertThat(accepted.status()).isEqualTo(SourceDocumentEntity.Status.STORED);
        assertThat(persistedIntent.getState()).isEqualTo(SourceUploadEntity.State.COMPLETED);
        verify(objectStore).put(any(), any(), any());
        verify(sourceDocumentRepository).save(any(SourceDocumentEntity.class));

        assertThatThrownBy(() -> service.complete(
                "subject",
                intent.uploadReference(),
                new MockMultipartFile("file", "notes.txt", "text/plain", content)
        )).isInstanceOfSatisfying(
                BrowserSourceConflictException.class,
                exception -> assertThat(exception.problemCode()).isEqualTo("UPLOAD_ALREADY_COMPLETED")
        );
    }

    @Test
    void rejectsOversizedAndUnsupportedIntentBeforeCreatingState() {
        assertThatThrownBy(() -> service.createIntent(
                "subject",
                new CreateSourceUploadRequestDto("large.pdf", "application/pdf", 2048, "0".repeat(64))
        )).isInstanceOfSatisfying(
                BrowserSourceValidationException.class,
                exception -> assertThat(exception.problemCode()).isEqualTo("DOCUMENT_TOO_LARGE")
        );

        assertThatThrownBy(() -> service.createIntent(
                "subject",
                new CreateSourceUploadRequestDto("image.png", "image/png", 12, "0".repeat(64))
        )).isInstanceOfSatisfying(
                BrowserSourceValidationException.class,
                exception -> assertThat(exception.problemCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE")
        );
    }
}
