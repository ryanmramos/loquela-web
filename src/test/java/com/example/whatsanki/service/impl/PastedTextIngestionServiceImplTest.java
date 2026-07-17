package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.PassageEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.IngestTextJobPayloadDto;
import com.example.whatsanki.dto.PassageDraftDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.PassageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.BackgroundJobService;
import com.example.whatsanki.service.exception.PastedTextLimitExceededException;
import com.example.whatsanki.service.SensitiveDataEncryptionService;
import com.example.whatsanki.service.util.PassageSegmenter;
import com.example.whatsanki.service.util.TextNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PastedTextIngestionServiceImplTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private InboundMessageRepository inboundMessageRepository;
    @Mock private SourceDocumentRepository sourceDocumentRepository;
    @Mock private PassageRepository passageRepository;
    @Mock private BackgroundJobService backgroundJobService;
    @Mock private SensitiveDataEncryptionService encryptionService;
    @Mock private TextNormalizer textNormalizer;
    @Mock private PassageSegmenter passageSegmenter;

    private WhatsAnkiConfigurationDto configuration;
    private PastedTextIngestionServiceImpl service;

    @BeforeEach
    void setUp() {
        configuration = new WhatsAnkiConfigurationDto();
        service = new PastedTextIngestionServiceImpl(
                appUserRepository,
                inboundMessageRepository,
                sourceDocumentRepository,
                passageRepository,
                backgroundJobService,
                encryptionService,
                textNormalizer,
                passageSegmenter,
                configuration
        );
    }

    @Test
    void transactionallyStagesTextCreatesSourceAndEnqueuesIdOnlyJob() {
        UserId userId = UserId.random();
        InboundMessageId inboundMessageId = InboundMessageId.random();
        InboundMessageEntity inboundMessage = inboundMessage(inboundMessageId, userId);
        AppUserEntity user = activeUser(userId, "es-MX");
        byte[] encrypted = "encrypted".getBytes(StandardCharsets.UTF_8);
        when(sourceDocumentRepository.findByUserIdAndInboundMessageId(userId.value(), inboundMessageId.value()))
                .thenReturn(Optional.empty());
        when(inboundMessageRepository.findByIdAndUserId(inboundMessageId.value(), userId.value()))
                .thenReturn(Optional.of(inboundMessage));
        when(appUserRepository.findById(userId.value())).thenReturn(Optional.of(user));
        when(encryptionService.encrypt(eq("El árbol."), any(String.class))).thenReturn(encrypted);
        when(backgroundJobService.enqueue(eq(userId), eq(BackgroundJobEntity.Type.INGEST_TEXT), any()))
                .thenReturn(JobId.random());

        var accepted = service.accept(userId, inboundMessageId, "El árbol.");

        assertThat(accepted.characterCount()).isEqualTo(9);
        assertThat(inboundMessage.getBodyCiphertext()).isSameAs(encrypted);
        ArgumentCaptor<SourceDocumentEntity> sourceCaptor = ArgumentCaptor.forClass(SourceDocumentEntity.class);
        verify(sourceDocumentRepository).save(sourceCaptor.capture());
        SourceDocumentEntity source = sourceCaptor.getValue();
        assertThat(source.getId()).isEqualTo(accepted.sourceId().value());
        assertThat(source.getUserId()).isEqualTo(userId.value());
        assertThat(source.getInboundMessageId()).isEqualTo(inboundMessageId.value());
        assertThat(source.getKind()).isEqualTo(SourceDocumentEntity.Kind.PASTED_TEXT);
        assertThat(source.getStatus()).isEqualTo(SourceDocumentEntity.Status.RECEIVED);
        assertThat(source.getLanguage()).isEqualTo("es-MX");
        verify(backgroundJobService).enqueue(
                userId,
                BackgroundJobEntity.Type.INGEST_TEXT,
                IngestTextJobPayloadDto.create(accepted.sourceId()).toMap()
        );
    }

    @Test
    void reusesSourceWhenSameInboundCommandIsDispatchedAgain() {
        UserId userId = UserId.random();
        InboundMessageId inboundMessageId = InboundMessageId.random();
        SourceDocumentEntity existing = source(SourceId.random(), userId, inboundMessageId);
        when(sourceDocumentRepository.findByUserIdAndInboundMessageId(userId.value(), inboundMessageId.value()))
                .thenReturn(Optional.of(existing));

        var accepted = service.accept(userId, inboundMessageId, "same text");

        assertThat(accepted.sourceId().value()).isEqualTo(existing.getId());
        verifyNoInteractions(inboundMessageRepository, backgroundJobService, encryptionService);
    }

    @Test
    void rejectsOversizedTextBeforeWritingAnything() {
        configuration.getIngestion().setMaxExtractedCharacters(5);

        assertThatThrownBy(() -> service.accept(
                UserId.random(), InboundMessageId.random(), "123456"
        )).isInstanceOf(PastedTextLimitExceededException.class);
        verifyNoInteractions(
                appUserRepository,
                inboundMessageRepository,
                sourceDocumentRepository,
                passageRepository,
                backgroundJobService,
                encryptionService
        );
    }

    @Test
    void convertsStagedTextToOrderedPassagesAndClearsCiphertext() {
        UserId userId = UserId.random();
        InboundMessageId inboundMessageId = InboundMessageId.random();
        SourceId sourceId = SourceId.random();
        SourceDocumentEntity source = source(sourceId, userId, inboundMessageId);
        InboundMessageEntity inboundMessage = inboundMessage(inboundMessageId, userId);
        inboundMessage.setBodyCiphertext(new byte[]{1, 2, 3});
        when(sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value()))
                .thenReturn(Optional.of(source));
        when(inboundMessageRepository.findByIdAndUserId(inboundMessageId.value(), userId.value()))
                .thenReturn(Optional.of(inboundMessage));
        when(encryptionService.decrypt(eq(inboundMessage.getBodyCiphertext()), any(String.class)))
                .thenReturn("Uno aquí. Dos allí.");
        when(textNormalizer.normalizeDisplay("Uno aquí. Dos allí."))
                .thenReturn("Uno aquí. Dos allí.");
        List<PassageDraftDto> drafts = List.of(
                new PassageDraftDto("Uno aquí.", Map.of("paragraphIndex", 0, "startCharacter", 0, "endCharacter", 9), 2),
                new PassageDraftDto("Dos allí.", Map.of("paragraphIndex", 0, "startCharacter", 10, "endCharacter", 19), 2)
        );
        when(passageSegmenter.segment(eq("Uno aquí. Dos allí."), any()))
                .thenReturn(drafts);
        when(textNormalizer.normalizeForSearch("Uno aquí.")).thenReturn("uno aquí.");
        when(textNormalizer.normalizeForSearch("Dos allí.")).thenReturn("dos allí.");

        service.ingest(userId, sourceId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<PassageEntity>> passagesCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(passageRepository).saveAll(passagesCaptor.capture());
        assertThat(passagesCaptor.getValue()).extracting(PassageEntity::getOrdinal)
                .containsExactly(0, 1);
        assertThat(passagesCaptor.getValue()).extracting(PassageEntity::getNormalizedText)
                .containsExactly("uno aquí.", "dos allí.");
        assertThat(source.getStatus()).isEqualTo(SourceDocumentEntity.Status.READY);
        assertThat(source.getCharacterCount()).isEqualTo(19L);
        assertThat(source.getPassageCount()).isEqualTo(2);
        assertThat(inboundMessage.getBodyCiphertext()).isNull();
        verify(inboundMessageRepository).save(inboundMessage);
        verify(sourceDocumentRepository).save(source);
    }

    @Test
    void treatsReadySourceAsIdempotentWorkerSuccess() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        SourceDocumentEntity source = source(sourceId, userId, InboundMessageId.random());
        source.setStatus(SourceDocumentEntity.Status.READY);
        when(sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value()))
                .thenReturn(Optional.of(source));

        service.ingest(userId, sourceId);

        verifyNoInteractions(inboundMessageRepository, passageRepository, encryptionService, passageSegmenter);
        verify(sourceDocumentRepository, never()).save(any());
    }

    @Test
    void marksNonReadySourceFailedWithoutReturningSensitiveDetail() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        SourceDocumentEntity source = source(sourceId, userId, InboundMessageId.random());
        when(sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value()))
                .thenReturn(Optional.of(source));

        service.markFailed(userId, sourceId, "EMPTY_TEXT");

        assertThat(source.getStatus()).isEqualTo(SourceDocumentEntity.Status.FAILED);
        assertThat(source.getErrorCode()).isEqualTo("EMPTY_TEXT");
        assertThat(source.getErrorDetailSafe()).isNull();
        verify(sourceDocumentRepository).save(source);
    }

    @Test
    void doesNotOverwriteAConcurrentDeletingTransitionWithFailed() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        SourceDocumentEntity source = source(sourceId, userId, InboundMessageId.random());
        source.setStatus(SourceDocumentEntity.Status.DELETING);
        when(sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value()))
                .thenReturn(Optional.of(source));

        service.markFailed(userId, sourceId, "INVALID_SOURCE_STATE");

        assertThat(source.getStatus()).isEqualTo(SourceDocumentEntity.Status.DELETING);
        verify(sourceDocumentRepository, never()).save(any());
    }

    private AppUserEntity activeUser(UserId userId, String sourceLanguage) {
        AppUserEntity user = new AppUserEntity();
        user.setId(userId.value());
        user.setStatus(AppUserEntity.Status.ACTIVE);
        user.setSourceLanguage(sourceLanguage);
        return user;
    }

    private InboundMessageEntity inboundMessage(InboundMessageId inboundMessageId, UserId userId) {
        InboundMessageEntity message = new InboundMessageEntity();
        message.setId(inboundMessageId.value());
        message.setUserId(userId.value());
        message.setCommandType(InboundMessageEntity.CommandType.ADD);
        return message;
    }

    private SourceDocumentEntity source(SourceId sourceId, UserId userId, InboundMessageId inboundMessageId) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(sourceId.value());
        source.setUserId(userId.value());
        source.setInboundMessageId(inboundMessageId.value());
        source.setKind(SourceDocumentEntity.Kind.PASTED_TEXT);
        source.setStatus(SourceDocumentEntity.Status.RECEIVED);
        source.setLanguage("es");
        return source;
    }
}
