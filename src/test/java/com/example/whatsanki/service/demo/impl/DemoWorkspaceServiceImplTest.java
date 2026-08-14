package com.example.whatsanki.service.demo.impl;

import com.example.whatsanki.dto.ExportedDeckDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.dto.TimedCaptionCueDto;
import com.example.whatsanki.dto.YouTubeSubtitleResultDto;
import com.example.whatsanki.dto.demo.DemoDtos;
import com.example.whatsanki.domain.VideoSourceEntity;
import com.example.whatsanki.service.dictionary.DictionaryProviderSelector;
import com.example.whatsanki.service.dictionary.FakeDictionaryProvider;
import com.example.whatsanki.service.export.DeckExporter;
import com.example.whatsanki.service.util.AnkiCardFieldMapper;
import com.example.whatsanki.service.util.PassageSegmenterImpl;
import com.example.whatsanki.service.util.TextNormalizerImpl;
import com.example.whatsanki.service.util.YouTubeUrlPolicy;
import com.example.whatsanki.service.youtube.YouTubePassageBuilder;
import com.example.whatsanki.service.youtube.YouTubeSubtitleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemoWorkspaceServiceImplTest {
    @TempDir Path temporaryDirectory;
    private WhatsAnkiConfigurationDto configuration;
    private InMemoryDemoWorkspaceStore store;
    private DemoWorkspaceServiceImpl service;
    private AtomicReference<Map<String, Object>> exportedSnapshot;
    private DemoYouTubeMetadataProbe metadataProbe;
    private YouTubeSubtitleProvider youtubeProvider;

    @BeforeEach
    void setUp() throws Exception {
        configuration = new WhatsAnkiConfigurationDto();
        configuration.getDemo().setEnabled(true);
        configuration.getDemo().setInactivityTimeout(Duration.ofHours(2));
        configuration.getDemo().setAbsoluteLifetime(Duration.ofHours(8));
        store = new InMemoryDemoWorkspaceStore(configuration);
        TextNormalizerImpl normalizer = new TextNormalizerImpl();
        DictionaryProviderSelector selector = mock(DictionaryProviderSelector.class);
        when(selector.activeProvider()).thenReturn(new FakeDictionaryProvider());
        exportedSnapshot = new AtomicReference<>();
        Path artifactDirectory = Files.createDirectory(temporaryDirectory.resolve("artifact"));
        Path artifact = Files.writeString(artifactDirectory.resolve("deck.apkg"), "test deck");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact));
        DeckExporter exporter = snapshot -> {
            exportedSnapshot.set(snapshot);
            return new ExportedDeckDto(artifact, 9, digest);
        };
        metadataProbe = mock(DemoYouTubeMetadataProbe.class);
        youtubeProvider = mock(YouTubeSubtitleProvider.class);
        service = new DemoWorkspaceServiceImpl(
                configuration, store, new DemoRateLimiter(), new NoOpDemoBotVerificationService(),
                normalizer, new PassageSegmenterImpl(configuration), selector,
                new AnkiCardFieldMapper(), exporter, new YouTubeUrlPolicy(),
                metadataProbe, youtubeProvider,
                new YouTubePassageBuilder(normalizer, configuration)
        );
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
        store.shutdown();
    }

    @Test
    void startsLazilyAndKeepsOnlyTheOpaqueReferenceInTheSession() {
        MockHttpServletRequest request = request();

        DemoDtos.WorkspaceView started = service.start(request);
        DemoDtos.WorkspaceView sameWorkspace = service.start(request);

        assertThat(sameWorkspace.createdAt()).isEqualTo(started.createdAt());
        assertThat(request.getSession().getAttributeNames().asIterator())
                .toIterable().containsExactly(DemoWorkspaceServiceImpl.SESSION_ATTRIBUTE);
        assertThat(request.getSession().getAttribute(DemoWorkspaceServiceImpl.SESSION_ATTRIBUTE))
                .isInstanceOf(String.class)
                .asString().matches("[0-9a-f-]{36}");
    }

    @Test
    void featureFlagFailsClosedBeforeCreatingAWorkspace() {
        configuration.getDemo().setEnabled(false);
        MockHttpServletRequest request = request();

        assertThat(service.capabilities()).extracting(DemoDtos.Capabilities::enabled).isEqualTo(false);
        assertThatThrownBy(() -> service.start(request)).hasMessage("DEMO_UNAVAILABLE");
        assertThat(request.getSession().getAttribute(DemoWorkspaceServiceImpl.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void isolatesSessionsAndCleansTheWorkspaceBeforeSignIn() {
        MockHttpServletRequest first = request();
        service.start(first);
        MockHttpSession secondSession = new MockHttpSession();

        assertThatThrownBy(() -> service.get(secondSession))
                .isInstanceOf(com.example.whatsanki.service.exception.DemoException.class)
                .hasMessage("DEMO_EXPIRED");

        service.cleanupForSignIn(first.getSession());
        assertThat(first.getSession().getAttribute(DemoWorkspaceServiceImpl.SESSION_ATTRIBUTE)).isNull();
        assertThatThrownBy(() -> service.get(first.getSession()))
                .hasMessage("DEMO_EXPIRED");
    }

    @Test
    void pastedTextLookupCardsUndoAndExportStayInMemoryAndAreRevisionCached() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpSession session = (MockHttpSession) request.getSession();
        service.start(request);
        service.ingestPastedText(session,
                new DemoDtos.PastedTextRequest("Quiero vivir bien. Vivimos cerca del mar."), request);
        DemoDtos.WorkspaceView ready = awaitReady(session);

        assertThat(ready.sources()).singleElement().satisfies(source -> {
            assertThat(source.state()).isEqualTo(DemoDtos.SourceState.READY);
            assertThat(source.passageCount()).isPositive();
        });
        DemoDtos.LookupView lookup = service.lookup(session, new DemoDtos.LookupRequest("vivir"));
        assertThat(lookup.definitions()).isNotEmpty();
        assertThat(lookup.contexts()).isNotEmpty();

        DemoDtos.CardMutation definition = service.saveCard(session,
                new DemoDtos.SaveCardRequest(lookup.reference(), DemoDtos.CardKind.DEFINITION, null));
        DemoDtos.CardMutation duplicate = service.saveCard(session,
                new DemoDtos.SaveCardRequest(lookup.reference(), DemoDtos.CardKind.DEFINITION, null));
        DemoDtos.CardMutation contextual = service.saveCard(session,
                new DemoDtos.SaveCardRequest(lookup.reference(), DemoDtos.CardKind.CONTEXT,
                        lookup.contexts().get(0).reference()));
        assertThat(duplicate.alreadyAdded()).isTrue();
        assertThat(service.get(session).cards()).hasSize(2);

        DemoDtos.CardRemoval removal = service.removeCard(session, contextual.card().reference());
        service.undoCard(session, contextual.card().reference(), removal.undoToken());
        assertThat(service.get(session).cards()).hasSize(2);

        DemoDtos.ExportView firstExport = service.export(session, request);
        DemoDtos.ExportView cachedExport = service.export(session, request);
        assertThat(firstExport.reused()).isFalse();
        assertThat(cachedExport.reused()).isTrue();
        assertThat(exportedSnapshot.get()).containsEntry("templateVersion", "whatsanki-context-v1");
        assertThat(exportedSnapshot.get().get("deck").toString()).contains("Loquela Demo");
        assertThat(service.download(session, firstExport.reference()).contentLength()).isEqualTo(9);
        assertThat(definition.card().example()).isEmpty();
    }

    @Test
    void sourceLanguageChangeRequiresConfirmationAndClearsSourcesButKeepsCards() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpSession session = (MockHttpSession) request.getSession();
        service.start(request);
        service.ingestPastedText(session, new DemoDtos.PastedTextRequest("Quiero vivir bien."), request);
        awaitReady(session);

        assertThatThrownBy(() -> service.languages(session,
                new DemoDtos.LanguagesRequest("en", "es", false)))
                .hasMessage("DEMO_SOURCE_LANGUAGE_CONFIRMATION_REQUIRED");
        DemoDtos.WorkspaceView changed = service.languages(session,
                new DemoDtos.LanguagesRequest("en", "es", true));

        assertThat(changed.sources()).isEmpty();
        assertThat(changed.sourceLanguage()).isEqualTo("en");
        assertThat(changed.targetLanguage()).isEqualTo("es");
    }

    @Test
    void storeEnforcesInactivityAndCapacityWithoutChangingTheHttpSessionLifetime() {
        configuration.getDemo().setInactivityTimeout(Duration.ofSeconds(1));
        configuration.getDemo().setAbsoluteLifetime(Duration.ofHours(1));
        configuration.getDemo().setMaxWorkspaces(1);
        InMemoryDemoWorkspaceStore localStore = new InMemoryDemoWorkspaceStore(configuration);
        Instant now = Instant.parse("2026-08-14T12:00:00Z");
        DemoWorkspace first = localStore.create(now);

        assertThat(localStore.resolve(first.id, now.plusSeconds(2))).isNull();
        DemoWorkspace replacement = localStore.create(now.plusSeconds(3));
        DemoWorkspace newest = localStore.create(now.plusSeconds(4));
        assertThat(localStore.resolve(replacement.id, now.plusSeconds(4))).isNull();
        assertThat(localStore.resolve(newest.id, now.plusSeconds(4))).isNotNull();
        localStore.shutdown();
    }

    @Test
    void ingestionKillSwitchLeavesTheWorkspaceAndLookupFeaturesAvailable() {
        MockHttpServletRequest request = request();
        MockHttpSession session = (MockHttpSession) request.getSession();
        service.start(request);
        configuration.getDemo().setIngestionEnabled(false);

        assertThatThrownBy(() -> service.ingestPastedText(session,
                new DemoDtos.PastedTextRequest("Quiero vivir."), request))
                .hasMessage("DEMO_INGESTION_DISABLED");
        assertThat(service.lookup(session, new DemoDtos.LookupRequest("vivir")).definitions()).isNotEmpty();
    }

    @Test
    void extractsPdfTextWithPageAttributionAndRejectsWorkspaceTextOverflow() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpSession session = (MockHttpSession) request.getSession();
        service.start(request);
        byte[] pdf = readablePdf("Quiero vivir cerca del mar.");
        service.ingestPdf(session, new MockMultipartFile("file", "sample.pdf",
                "application/pdf", pdf), request);

        assertThat(awaitReady(session).sources()).singleElement()
                .satisfies(source -> assertThat(source.state()).isEqualTo(DemoDtos.SourceState.READY));

        service.removeSource(session, DemoDtos.SourceType.PDF);
        configuration.getDemo().setWorkspaceMaxIndexedCharacters(5);
        service.ingestPastedText(session, new DemoDtos.PastedTextRequest("Quiero vivir."), request);
        assertThat(awaitReady(session).sources()).singleElement().satisfies(source -> {
            assertThat(source.state()).isEqualTo(DemoDtos.SourceState.FAILED);
            assertThat(source.safeErrorCode()).isEqualTo("DEMO_TOTAL_TEXT_LIMIT");
        });
    }

    @Test
    void probesYouTubeDurationBeforeBuildingCaptionPassages() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpSession session = (MockHttpSession) request.getSession();
        service.start(request);
        when(youtubeProvider.fetch("dQw4w9WgXcQ", "es", true)).thenReturn(new YouTubeSubtitleResultDto(
                "A short lesson", "Example channel", 60_000L, "es",
                VideoSourceEntity.CaptionKind.MANUAL, "test", Instant.now(),
                java.util.List.of(new TimedCaptionCueDto(0, 2_000, "Quiero vivir bien."))));

        service.ingestYouTube(session,
                new DemoDtos.YouTubeRequest("https://www.youtube.com/watch?v=dQw4w9WgXcQ"), request);

        assertThat(awaitReady(session).sources()).singleElement().satisfies(source -> {
            assertThat(source.type()).isEqualTo(DemoDtos.SourceType.YOUTUBE);
            assertThat(source.label()).isEqualTo("A short lesson");
            assertThat(source.youtubeVideoId()).isEqualTo("dQw4w9WgXcQ");
        });
        org.mockito.Mockito.verify(metadataProbe).requireAllowedDuration("dQw4w9WgXcQ");
    }

    private DemoDtos.WorkspaceView awaitReady(MockHttpSession session) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            DemoDtos.WorkspaceView view = service.get(session);
            if (view.sources().stream().allMatch(source ->
                    source.state() == DemoDtos.SourceState.READY || source.state() == DemoDtos.SourceState.FAILED)) {
                return view;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("demo ingestion did not finish");
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.setSession(new MockHttpSession());
        return request;
    }

    private byte[] readablePdf(String text) throws Exception {
        Path path = temporaryDirectory.resolve("readable.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(path.toFile());
        }
        return Files.readAllBytes(path);
    }
}
