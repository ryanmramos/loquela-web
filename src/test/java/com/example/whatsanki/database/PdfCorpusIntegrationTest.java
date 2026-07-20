package com.example.whatsanki.database;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.CorpusSearchOptionsDto;
import com.example.whatsanki.dto.StoredObjectMetadataDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.PassageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.document.DocumentExtractionLifecycleService;
import com.example.whatsanki.service.document.DocumentExtractionServiceImpl;
import com.example.whatsanki.service.document.DocumentTextExtractor;
import com.example.whatsanki.service.document.PdfDocumentTextExtractorImpl;
import com.example.whatsanki.service.lookup.CorpusSearch;
import com.example.whatsanki.service.storage.ObjectStore;
import com.example.whatsanki.service.util.PassageSegmenter;
import com.example.whatsanki.service.util.TextNormalizer;
import com.example.whatsanki.support.PdfTestDocumentFactory;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

class PdfCorpusIntegrationTest extends PostgreSqlIntegrationTest {

    private final AppUserRepository appUserRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final PassageRepository passageRepository;
    private final CorpusSearch corpusSearch;
    private final DocumentExtractionLifecycleService extractionLifecycleService;
    private final PassageSegmenter passageSegmenter;
    private final TextNormalizer textNormalizer;
    private final WhatsAnkiConfigurationDto configuration;
    private final ExecutorService documentExtractionExecutor;

    PdfCorpusIntegrationTest(
            AppUserRepository appUserRepository,
            SourceDocumentRepository sourceDocumentRepository,
            PassageRepository passageRepository,
            CorpusSearch corpusSearch,
            DocumentExtractionLifecycleService extractionLifecycleService,
            PassageSegmenter passageSegmenter,
            TextNormalizer textNormalizer,
            WhatsAnkiConfigurationDto configuration,
            ExecutorService documentExtractionExecutor
    ) {
        this.appUserRepository = appUserRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.passageRepository = passageRepository;
        this.corpusSearch = corpusSearch;
        this.extractionLifecycleService = extractionLifecycleService;
        this.passageSegmenter = passageSegmenter;
        this.textNormalizer = textNormalizer;
        this.configuration = configuration;
        this.documentExtractionExecutor = documentExtractionExecutor;
    }

    @Test
    void storedPdfBecomesReadyPageAwareAndSearchableInItsSourceLanguage() {
        UserId userId = user();
        SourceDocumentEntity source = storedPdf(userId);
        byte[] pdf = PdfTestDocumentFactory.textPdf(
                "El árbol antiguo está junto al río.",
                "La niña descansa bajo el árbol cada tarde."
        );

        DocumentTextExtractor extractor = new PdfDocumentTextExtractorImpl(
                configuration,
                documentExtractionExecutor
        );
        var extractionService = new DocumentExtractionServiceImpl(
                extractionLifecycleService,
                new InMemoryReadOnlyObjectStore(source.getObjectKey(), pdf),
                passageSegmenter,
                textNormalizer,
                configuration,
                List.of(extractor)
        );

        extractionService.extractAndIndex(userId, new SourceId(source.getId()));

        SourceDocumentEntity ready = sourceDocumentRepository
                .findByIdAndUserId(source.getId(), userId.value())
                .orElseThrow();
        assertThat(ready.getStatus()).isEqualTo(SourceDocumentEntity.Status.READY);
        assertThat(ready.getPassageCount()).isEqualTo(2);
        assertThat(ready.getCharacterCount()).isPositive();

        var passages = passageRepository.findAllByUserIdAndSourceIdOrderByOrdinal(
                userId.value(), source.getId()
        );
        assertThat(passages).hasSize(2);
        assertThat(passages)
                .extracting(passage -> passage.getLocation().get("pageNumber"))
                .containsExactly(1, 2);

        var results = corpusSearch.findExamples(
                userId,
                "árbol",
                new CorpusSearchOptionsDto(new LanguageTag("es"), 5, 5)
        );
        assertThat(results).hasSize(2);
        assertThat(results)
                .allSatisfy(result -> assertThat(result.location()).containsKey("pageNumber"));

        assertThat(corpusSearch.findExamples(
                userId,
                "árbol",
                new CorpusSearchOptionsDto(new LanguageTag("en"), 5, 5)
        )).isEmpty();
    }

    private UserId user() {
        String identity = "pdf-owner-" + UUID.randomUUID();
        AppUserEntity user = new AppUserEntity();
        user.setWhatsappAddressHash(identity.getBytes(StandardCharsets.UTF_8));
        user.setWhatsappAddressCiphertext(("encrypted-" + identity).getBytes(StandardCharsets.UTF_8));
        user.setSourceLanguage("es");
        user.setDefinitionLanguage("en");
        user.setStatus(AppUserEntity.Status.ACTIVE);
        return new UserId(appUserRepository.saveAndFlush(user).getId());
    }

    private SourceDocumentEntity storedPdf(UserId userId) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setUserId(userId.value());
        source.setKind(SourceDocumentEntity.Kind.PDF);
        source.setDisplayName("lectura.pdf");
        source.setStatus(SourceDocumentEntity.Status.STORED);
        source.setDetectedMediaType("application/pdf");
        source.setObjectKey("users/" + userId.value() + "/sources/" + UUID.randomUUID() + "/original");
        source.setOriginalStoredAt(Instant.now());
        source.setLanguage("es");
        source.setMetadata(new LinkedHashMap<>());
        return sourceDocumentRepository.save(source);
    }

    private record InMemoryReadOnlyObjectStore(String expectedKey, byte[] content) implements ObjectStore {
        @Override
        public InputStream open(String objectKey) {
            assertThat(objectKey).isEqualTo(expectedKey);
            return new ByteArrayInputStream(content);
        }

        @Override
        public void put(String objectKey, Path source, StoredObjectMetadataDto metadata) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String objectKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public URI createPresignedGetUrl(String objectKey, Duration lifetime) {
            throw new UnsupportedOperationException();
        }
    }
}
