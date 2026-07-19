package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.PassageDraftDto;
import com.example.whatsanki.repository.PassageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.util.TextNormalizerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentExtractionLifecycleServiceImplTest {

    @Mock private SourceDocumentRepository sourceRepository;
    @Mock private PassageRepository passageRepository;
    private DocumentExtractionLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentExtractionLifecycleServiceImpl(
                sourceRepository, passageRepository, new TextNormalizerImpl()
        );
    }

    @Test
    void preparesStoredPdfForExtraction() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        SourceDocumentEntity source = source(sourceId, SourceDocumentEntity.Status.STORED);
        when(sourceRepository.findByIdAndUserId(sourceId.value(), userId.value()))
                .thenReturn(Optional.of(source));

        var preparation = service.prepare(userId, sourceId);

        assertThat(preparation.objectKey()).isEqualTo(source.getObjectKey());
        assertThat(preparation.language().value()).isEqualTo("es");
        assertThat(source.getStatus()).isEqualTo(SourceDocumentEntity.Status.EXTRACTING);
    }

    @Test
    void atomicallyReplacesPassagesAndMarksReady() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        SourceDocumentEntity source = source(sourceId, SourceDocumentEntity.Status.EXTRACTING);
        when(sourceRepository.findByIdAndUserId(sourceId.value(), userId.value()))
                .thenReturn(Optional.of(source));
        PassageDraftDto draft = new PassageDraftDto(
                "El contexto del documento.",
                Map.of("pageNumber", 2),
                4
        );

        service.complete(userId, sourceId, List.of(draft), 25);

        verify(passageRepository).deleteAllByUserIdAndSourceId(userId.value(), sourceId.value());
        ArgumentCaptor<Iterable<com.example.whatsanki.domain.PassageEntity>> saved =
                ArgumentCaptor.forClass(Iterable.class);
        verify(passageRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(passage -> {
            assertThat(passage.getDisplayText()).isEqualTo(draft.displayText());
            assertThat(passage.getLocation()).containsEntry("pageNumber", 2);
        });
        assertThat(source.getStatus()).isEqualTo(SourceDocumentEntity.Status.READY);
        assertThat(source.getPassageCount()).isEqualTo(1);
        assertThat(source.getCharacterCount()).isEqualTo(25);
    }

    private SourceDocumentEntity source(SourceId sourceId, SourceDocumentEntity.Status status) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(sourceId.value());
        source.setKind(SourceDocumentEntity.Kind.PDF);
        source.setStatus(status);
        source.setObjectKey("users/u/sources/s/original");
        source.setLanguage("es");
        source.setMetadata(new LinkedHashMap<>());
        return source;
    }
}
