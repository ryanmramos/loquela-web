package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.DocumentExtractionPreparationDto;
import com.example.whatsanki.dto.ExtractedDocumentDto;
import com.example.whatsanki.dto.ExtractedTextSectionDto;
import com.example.whatsanki.dto.PassageDraftDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.DocumentExtractionLifecycleService;
import com.example.whatsanki.service.DocumentTextExtractor;
import com.example.whatsanki.service.ObjectStore;
import com.example.whatsanki.service.util.PassageSegmenterImpl;
import com.example.whatsanki.service.util.TextNormalizerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentExtractionServiceImplTest {

    @Mock private DocumentExtractionLifecycleService lifecycleService;
    @Mock private ObjectStore objectStore;
    @Mock private DocumentTextExtractor extractor;

    private DocumentExtractionServiceImpl service;

    @BeforeEach
    void setUp() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        when(extractor.supportedKind()).thenReturn(SourceDocumentEntity.Kind.PDF);
        service = new DocumentExtractionServiceImpl(
                lifecycleService,
                objectStore,
                new PassageSegmenterImpl(configuration),
                new TextNormalizerImpl(),
                configuration,
                List.of(extractor)
        );
    }

    @Test
    void readsExtractsSegmentsAndPreservesPageNumber() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        String objectKey = "users/u/sources/s/original";
        when(lifecycleService.prepare(userId, sourceId)).thenReturn(
                new DocumentExtractionPreparationDto(
                        false, objectKey, SourceDocumentEntity.Kind.PDF, new LanguageTag("es")
                )
        );
        when(objectStore.open(objectKey)).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(extractor.extract(any(byte[].class))).thenReturn(new ExtractedDocumentDto(
                List.of(new ExtractedTextSectionDto(
                        4,
                        "El arbol aparece aqui. Esta frase conserva el contexto."
                )),
                55
        ));

        service.extractAndIndex(userId, sourceId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PassageDraftDto>> passages = ArgumentCaptor.forClass(List.class);
        verify(lifecycleService).complete(eq(userId), eq(sourceId), passages.capture(), anyLong());
        assertThat(passages.getValue()).isNotEmpty();
        assertThat(passages.getValue().get(0).location()).containsEntry("pageNumber", 4);
        assertThat(passages.getValue().get(0).displayText()).contains("arbol");
    }
}
