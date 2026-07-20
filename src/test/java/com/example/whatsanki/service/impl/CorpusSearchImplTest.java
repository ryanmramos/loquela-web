package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.PassageEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.CorpusSearchOptionsDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.PassageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.util.TextNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorpusSearchImplTest {

    @Mock private PassageRepository passageRepository;
    @Mock private SourceDocumentRepository sourceDocumentRepository;
    @Mock private TextNormalizer textNormalizer;

    private WhatsAnkiConfigurationDto configuration;
    private CorpusSearchImpl service;

    @BeforeEach
    void setUp() {
        configuration = new WhatsAnkiConfigurationDto();
        service = new CorpusSearchImpl(
                passageRepository,
                sourceDocumentRepository,
                textNormalizer,
                configuration
        );
    }

    @Test
    void preservesRankWithinSourcesWhileDiversifyingAcrossSources() {
        UserId userId = UserId.random();
        UUID sourceA = UUID.randomUUID();
        UUID sourceB = UUID.randomUUID();
        PassageEntity a1 = passage(sourceA, 0, "Best A");
        PassageEntity a2 = passage(sourceA, 1, "Second A");
        PassageEntity b1 = passage(sourceB, 0, "Best B");
        when(textNormalizer.normalizeForSearch(" ÁRBOL ")).thenReturn("árbol");
        when(passageRepository.searchRankedReadyPassages(
                eq(userId.value()), eq("es"), eq("árbol"), eq(320), eq(PageRequest.of(0, 15))
        )).thenReturn(List.of(a1, a2, b1));
        when(sourceDocumentRepository.findAllByUserIdAndIdIn(eq(userId.value()), anySet()))
                .thenReturn(List.of(source(sourceA, "Source A"), source(sourceB, "Source B")));

        var results = service.findExamples(
                userId,
                " ÁRBOL ",
                new CorpusSearchOptionsDto(new LanguageTag("es"), 3, 2)
        );

        assertThat(results).extracting(result -> result.displayText())
                .containsExactly("Best A", "Best B", "Second A");
        assertThat(results).extracting(result -> result.sourceDisplayName())
                .containsExactly("Source A", "Source B", "Source A");
        verify(sourceDocumentRepository).findAllByUserIdAndIdIn(
                userId.value(), Set.of(sourceA, sourceB)
        );
    }

    @Test
    void rejectsBlankOversizedOrUnboundedSearchesBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.findExamples(
                UserId.random(),
                " ",
                new CorpusSearchOptionsDto(new LanguageTag("es"), 1, 1)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.findExamples(
                UserId.random(),
                "árbol",
                new CorpusSearchOptionsDto(new LanguageTag("es"), 21, 1)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private PassageEntity passage(UUID sourceId, int ordinal, String text) {
        PassageEntity passage = new PassageEntity();
        passage.setId(UUID.randomUUID());
        passage.setSourceId(sourceId);
        passage.setOrdinal(ordinal);
        passage.setDisplayText(text);
        passage.setLocation(new LinkedHashMap<>());
        return passage;
    }

    private SourceDocumentEntity source(UUID sourceId, String name) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(sourceId);
        source.setKind(SourceDocumentEntity.Kind.PASTED_TEXT);
        source.setDisplayName(name);
        source.setLanguage("es");
        source.setCreatedAt(Instant.now());
        return source;
    }
}
