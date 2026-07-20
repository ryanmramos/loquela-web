package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ContextExampleDto;
import com.example.whatsanki.dto.CorpusSearchOptionsDto;
import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionaryLookupRequestDto;
import com.example.whatsanki.dto.DictionaryProvenanceDto;
import com.example.whatsanki.dto.DictionarySenseDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LookupAcceptanceDto;
import com.example.whatsanki.dto.LookupExecutionPreparationDto;
import com.example.whatsanki.dto.LookupExecutionResultDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.CorpusSearch;
import com.example.whatsanki.service.DictionaryProvider;
import com.example.whatsanki.service.DictionaryProviderSelector;
import com.example.whatsanki.service.LookupSessionLifecycleService;
import com.example.whatsanki.service.UserLanguageSettingsService;
import com.example.whatsanki.service.exception.DictionaryProviderException;
import com.example.whatsanki.service.exception.LookupExecutionException;
import com.example.whatsanki.service.exception.UnsupportedDictionaryLanguagePairException;
import com.example.whatsanki.service.util.TextNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LookupServiceImplTest {

    @Mock private UserLanguageSettingsService languageSettingsService;
    @Mock private LookupSessionLifecycleService lifecycleService;
    @Mock private DictionaryProviderSelector providerSelector;
    @Mock private DictionaryProvider provider;
    @Mock private CorpusSearch corpusSearch;
    @Mock private TextNormalizer textNormalizer;

    private WhatsAnkiConfigurationDto configuration;
    private LookupServiceImpl service;

    @BeforeEach
    void setUp() {
        configuration = new WhatsAnkiConfigurationDto();
        service = new LookupServiceImpl(
                languageSettingsService,
                lifecycleService,
                providerSelector,
                corpusSearch,
                textNormalizer,
                configuration
        );
    }

    @Test
    void snapshotsBothLanguagesWhenLookupIsAccepted() {
        UserId userId = UserId.random();
        InboundMessageId inboundMessageId = InboundMessageId.random();
        LanguagePairDto pair = new LanguagePairDto("es-MX", "en");
        LookupAcceptanceDto accepted = new LookupAcceptanceDto(
                LookupSessionId.random(), pair, Instant.now().plusSeconds(1_800)
        );
        when(textNormalizer.normalizeForSearch("Vivir")).thenReturn("vivir");
        when(languageSettingsService.getCurrent(userId)).thenReturn(pair);
        when(lifecycleService.accept(
                eq(userId), eq(inboundMessageId), eq("Vivir"), eq("vivir"), eq(pair), any(Instant.class)
        )).thenReturn(accepted);

        assertThat(service.accept(userId, inboundMessageId, "  Vivir  ")).isEqualTo(accepted);
        verify(languageSettingsService).getCurrent(userId);
    }

    @Test
    void usesSnapshotForProviderAndCorpusThenPersistsNumberedContexts() {
        UserId userId = UserId.random();
        LookupSessionId sessionId = LookupSessionId.random();
        LanguagePairDto pair = new LanguagePairDto("es", "en");
        DictionaryEntryDto entry = entry(pair);
        ContextExampleDto context = context();
        when(lifecycleService.prepare(eq(userId), eq(sessionId), any(Instant.class)))
                .thenReturn(preparation(sessionId, pair, false, false));
        when(providerSelector.activeProvider()).thenReturn(provider);
        when(provider.lookup(new DictionaryLookupRequestDto("vivir", pair)))
                .thenReturn(Optional.of(entry));
        when(provider.providerId()).thenReturn("fake");
        when(corpusSearch.findExamples(
                eq(userId),
                eq(List.of("vivir", "vivo", "vive")),
                eq(new CorpusSearchOptionsDto(new LanguageTag("es"), 5, 2))
        )).thenReturn(List.of(context));
        when(lifecycleService.complete(
                eq(userId), eq(sessionId), eq(entry), eq(List.of(context)), any(Instant.class)
        )).thenReturn(true);

        LookupExecutionResultDto result = service.execute(userId, sessionId);

        assertThat(result.outcome()).isEqualTo(LookupExecutionResultDto.Outcome.COMPLETED);
        assertThat(result.contextCount()).isEqualTo(1);
        ArgumentCaptor<DictionaryLookupRequestDto> providerRequest =
                ArgumentCaptor.forClass(DictionaryLookupRequestDto.class);
        verify(provider).lookup(providerRequest.capture());
        assertThat(providerRequest.getValue().languagePair()).isEqualTo(pair);
    }

    @Test
    void classifiesMissingDefinitionsAndContextAsPermanentWithoutCompletingSession() {
        UserId userId = UserId.random();
        LookupSessionId sessionId = LookupSessionId.random();
        LanguagePairDto pair = new LanguagePairDto("es", "en");
        when(lifecycleService.prepare(eq(userId), eq(sessionId), any(Instant.class)))
                .thenReturn(preparation(sessionId, pair, false, false));
        when(providerSelector.activeProvider()).thenReturn(provider);
        when(provider.lookup(any(DictionaryLookupRequestDto.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(userId, sessionId))
                .isInstanceOfSatisfying(LookupExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.PERMANENT);
                    assertThat(exception.errorCode()).isEqualTo("DEFINITION_NOT_FOUND");
                });
        verify(lifecycleService, never()).complete(any(), any(), any(), any(), any());

        DictionaryEntryDto entry = entry(pair);
        when(provider.lookup(any(DictionaryLookupRequestDto.class))).thenReturn(Optional.of(entry));
        when(corpusSearch.findExamples(any(), any(List.class), any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.execute(userId, sessionId))
                .isInstanceOfSatisfying(LookupExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.PERMANENT);
                    assertThat(exception.errorCode()).isEqualTo("CONTEXT_NOT_FOUND");
                });
    }

    @Test
    void classifiesUnsupportedPairAsPermanentWithoutSearchingCorpus() {
        UserId userId = UserId.random();
        LookupSessionId sessionId = LookupSessionId.random();
        LanguagePairDto pair = new LanguagePairDto("fr", "de");
        when(lifecycleService.prepare(eq(userId), eq(sessionId), any(Instant.class)))
                .thenReturn(preparation(sessionId, pair, false, false));
        when(providerSelector.activeProvider()).thenReturn(provider);
        when(provider.lookup(new DictionaryLookupRequestDto("vivir", pair)))
                .thenThrow(new UnsupportedDictionaryLanguagePairException("fake", pair));

        assertThatThrownBy(() -> service.execute(userId, sessionId))
                .isInstanceOfSatisfying(LookupExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.PERMANENT);
                    assertThat(exception.errorCode()).isEqualTo("UNSUPPORTED_DICTIONARY_PAIR");
                });
        verifyNoInteractions(corpusSearch);
    }

    @Test
    void classifiesProviderTimeoutAsTransientWithoutSearchingCorpus() {
        UserId userId = UserId.random();
        LookupSessionId sessionId = LookupSessionId.random();
        LanguagePairDto pair = new LanguagePairDto("es", "en");
        when(lifecycleService.prepare(eq(userId), eq(sessionId), any(Instant.class)))
                .thenReturn(preparation(sessionId, pair, false, false));
        when(providerSelector.activeProvider()).thenReturn(provider);
        when(provider.lookup(new DictionaryLookupRequestDto("vivir", pair)))
                .thenThrow(DictionaryProviderException.timeout("external", new RuntimeException("timed out")));

        assertThatThrownBy(() -> service.execute(userId, sessionId))
                .isInstanceOfSatisfying(LookupExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.TRANSIENT);
                    assertThat(exception.errorCode()).isEqualTo("PROVIDER_TIMEOUT");
                });
        verifyNoInteractions(corpusSearch);
    }

    @Test
    void expiresDelayedWorkWithoutCallingProviderOrCorpus() {
        UserId userId = UserId.random();
        LookupSessionId sessionId = LookupSessionId.random();
        when(lifecycleService.prepare(eq(userId), eq(sessionId), any(Instant.class)))
                .thenReturn(preparation(sessionId, new LanguagePairDto("es", "en"), false, true));

        LookupExecutionResultDto result = service.execute(userId, sessionId);

        assertThat(result.outcome()).isEqualTo(LookupExecutionResultDto.Outcome.EXPIRED);
        verify(lifecycleService).markExpired(eq(userId), eq(sessionId), any(Instant.class));
        verifyNoInteractions(providerSelector, corpusSearch);
    }

    private LookupExecutionPreparationDto preparation(
            LookupSessionId sessionId,
            LanguagePairDto pair,
            boolean completed,
            boolean expired
    ) {
        return new LookupExecutionPreparationDto(
                sessionId,
                "vivir",
                "vivir",
                pair,
                Instant.now().plusSeconds(1_800),
                completed,
                expired
        );
    }

    private DictionaryEntryDto entry(LanguagePairDto pair) {
        return new DictionaryEntryDto(
                "vivir",
                "vivir",
                List.of("vivo", "vive"),
                List.of(new DictionarySenseDto("verb", "to live", "fake:sense")),
                pair,
                new DictionaryProvenanceDto(
                        "fake", "v1", "fake:vivir", "", "TEST", "Test data", Map.of()
                )
        );
    }

    private ContextExampleDto context() {
        return new ContextExampleDto(
                UUID.randomUUID(),
                SourceId.random(),
                SourceDocumentEntity.Kind.PDF,
                "Book",
                new LanguageTag("es"),
                0,
                "Quiero vivir aquí.",
                Map.of("pageNumber", 4)
        );
    }
}
