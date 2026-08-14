package com.example.whatsanki.service.demo.impl;

import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.dto.AnkiCardFieldsDto;
import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionaryLookupRequestDto;
import com.example.whatsanki.dto.DictionarySenseDto;
import com.example.whatsanki.dto.ExportedDeckDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.NumberedContextExampleDto;
import com.example.whatsanki.dto.PassageDraftDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.dto.YouTubeSubtitleResultDto;
import com.example.whatsanki.dto.demo.DemoDtos;
import com.example.whatsanki.service.demo.DemoBotVerificationService;
import com.example.whatsanki.service.demo.DemoWorkspaceService;
import com.example.whatsanki.service.dictionary.DictionaryProvider;
import com.example.whatsanki.service.dictionary.DictionaryProviderSelector;
import com.example.whatsanki.service.exception.DemoException;
import com.example.whatsanki.service.export.DeckExporter;
import com.example.whatsanki.service.util.AnkiCardFieldMapper;
import com.example.whatsanki.service.util.PassageSegmenter;
import com.example.whatsanki.service.util.TextNormalizer;
import com.example.whatsanki.service.util.YouTubeUrlPolicy;
import com.example.whatsanki.service.youtube.YouTubePassageBuilder;
import com.example.whatsanki.service.youtube.YouTubeSubtitleProvider;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Service
@Slf4j
public class DemoWorkspaceServiceImpl implements DemoWorkspaceService {
    static final String SESSION_ATTRIBUTE = DemoWorkspaceServiceImpl.class.getName() + ".WORKSPACE";
    private static final Duration UNDO_LIFETIME = Duration.ofMinutes(1);
    private static final Pattern PDF_SIGNATURE = Pattern.compile("%PDF-[0-9].*");
    private static final Pattern YOUTUBE_VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    private final WhatsAnkiConfigurationDto configuration;
    private final DemoWorkspaceStore store;
    private final DemoRateLimiter rateLimiter;
    private final DemoBotVerificationService botVerificationService;
    private final TextNormalizer textNormalizer;
    private final PassageSegmenter passageSegmenter;
    private final DictionaryProvider dictionaryProvider;
    private final AnkiCardFieldMapper cardFieldMapper;
    private final DeckExporter deckExporter;
    private final YouTubeUrlPolicy youTubeUrlPolicy;
    private final DemoYouTubeMetadataProbe youTubeMetadataProbe;
    private final YouTubeSubtitleProvider youTubeSubtitleProvider;
    private final YouTubePassageBuilder youTubePassageBuilder;
    private final ThreadPoolExecutor ingestionExecutor;
    private final Semaphore exportSemaphore = new Semaphore(1);

    public DemoWorkspaceServiceImpl(
            WhatsAnkiConfigurationDto configuration,
            DemoWorkspaceStore store,
            DemoRateLimiter rateLimiter,
            DemoBotVerificationService botVerificationService,
            TextNormalizer textNormalizer,
            PassageSegmenter passageSegmenter,
            DictionaryProviderSelector dictionaryProviderSelector,
            AnkiCardFieldMapper cardFieldMapper,
            DeckExporter deckExporter,
            YouTubeUrlPolicy youTubeUrlPolicy,
            DemoYouTubeMetadataProbe youTubeMetadataProbe,
            YouTubeSubtitleProvider youTubeSubtitleProvider,
            YouTubePassageBuilder youTubePassageBuilder
    ) {
        this.configuration = configuration;
        this.store = store;
        this.rateLimiter = rateLimiter;
        this.botVerificationService = botVerificationService;
        this.textNormalizer = textNormalizer;
        this.passageSegmenter = passageSegmenter;
        this.dictionaryProvider = dictionaryProviderSelector.activeProvider();
        this.cardFieldMapper = cardFieldMapper;
        this.deckExporter = deckExporter;
        this.youTubeUrlPolicy = youTubeUrlPolicy;
        this.youTubeMetadataProbe = youTubeMetadataProbe;
        this.youTubeSubtitleProvider = youTubeSubtitleProvider;
        this.youTubePassageBuilder = youTubePassageBuilder;
        AtomicInteger sequence = new AtomicInteger();
        this.ingestionExecutor = new ThreadPoolExecutor(
                configuration.getDemo().getIngestionWorkers(),
                configuration.getDemo().getIngestionWorkers(),
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(configuration.getDemo().getIngestionQueueCapacity()),
                task -> {
                    Thread thread = new Thread(task, "demo-ingestion-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Override
    public DemoDtos.Capabilities capabilities() {
        WhatsAnkiConfigurationDto.Demo demo = configuration.getDemo();
        return new DemoDtos.Capabilities(
                demo.isEnabled(), demo.isEnabled() && demo.isIngestionEnabled(),
                List.copyOf(configuration.getLanguages().getSupportedPairs()),
                new DemoDtos.Limits(demo.getPdfMaxBytes(), demo.getPdfMaxPages(),
                        demo.getPdfMaxExtractedCharacters(), (int) demo.getYoutubeMaxDuration().toMinutes(),
                        demo.getPastedTextMaxCharacters(), demo.getWorkspaceMaxIndexedCharacters(),
                        demo.getMaxUniqueLookups(), demo.getMaxCards())
        );
    }

    @Override
    public DemoDtos.WorkspaceView start(HttpServletRequest request) {
        requireEnabled();
        botVerificationService.verify(request);
        HttpSession session = request.getSession(true);
        DemoWorkspace existing = resolveOptional(session);
        if (existing != null) return view(existing);
        rateLimiter.require(request, "workspace", configuration.getDemo().getSessionsPerIpHour(), Duration.ofHours(1));
        DemoWorkspace workspace = store.create(Instant.now());
        session.setAttribute(SESSION_ATTRIBUTE, workspace.id.toString());
        return view(workspace);
    }

    @Override
    public DemoDtos.WorkspaceView get(HttpSession session) {
        return view(requireWorkspace(session));
    }

    @Override
    public DemoDtos.WorkspaceView reset(HttpSession session) {
        DemoWorkspace current = requireWorkspace(session);
        DemoWorkspace replacement = store.reset(current.id, Instant.now());
        session.setAttribute(SESSION_ATTRIBUTE, replacement.id.toString());
        return view(replacement);
    }

    @Override
    public void exit(HttpSession session) {
        if (session == null) return;
        cleanupForSignIn(session);
        session.invalidate();
    }

    @Override
    public void cleanupForSignIn(HttpSession session) {
        if (session == null) return;
        UUID id = sessionWorkspaceId(session);
        session.removeAttribute(SESSION_ATTRIBUTE);
        if (id != null) store.remove(id);
    }

    @Override
    public DemoDtos.WorkspaceView languages(HttpSession session, DemoDtos.LanguagesRequest request) {
        DemoWorkspace workspace = requireWorkspace(session);
        LanguagePairDto pair = pair(request.sourceLanguage(), request.targetLanguage());
        requireSupportedPair(pair);
        synchronized (workspace) {
            boolean sourceChanged = !workspace.sourceLanguage.equals(pair.sourceLanguage());
            boolean targetChanged = !workspace.targetLanguage.equals(pair.definitionLanguage());
            if (sourceChanged && !request.confirmSourceChange() && !workspace.sources.isEmpty()) {
                throw problem(HttpStatus.CONFLICT, "DEMO_SOURCE_LANGUAGE_CONFIRMATION_REQUIRED");
            }
            if (sourceChanged) {
                clearSources(workspace);
                workspace.corpusRevision++;
            }
            if (sourceChanged || targetChanged) clearLookups(workspace);
            workspace.sourceLanguage = pair.sourceLanguage();
            workspace.targetLanguage = pair.definitionLanguage();
            return viewLocked(workspace);
        }
    }

    @Override
    public DemoDtos.WorkspaceView ingestPastedText(HttpSession session, DemoDtos.PastedTextRequest request,
                                                   HttpServletRequest servletRequest) {
        requireIngestion(servletRequest);
        DemoWorkspace workspace = requireWorkspace(session);
        String text = request.text();
        if (codePoints(text) > configuration.getDemo().getPastedTextMaxCharacters()) {
            throw problem(HttpStatus.PAYLOAD_TOO_LARGE, "PASTED_TEXT_TOO_LARGE");
        }
        queue(workspace, DemoDtos.SourceType.PASTED_TEXT, "Pasted text", text,
                () -> pastedPassages(text, workspace.sourceLanguage));
        return view(workspace);
    }

    @Override
    public DemoDtos.WorkspaceView ingestPdf(HttpSession session, MultipartFile file,
                                            HttpServletRequest servletRequest) {
        requireIngestion(servletRequest);
        DemoWorkspace workspace = requireWorkspace(session);
        if (file == null || file.isEmpty()) throw problem(HttpStatus.BAD_REQUEST, "INVALID_PDF");
        if (file.getSize() > configuration.getDemo().getPdfMaxBytes()) {
            throw problem(HttpStatus.PAYLOAD_TOO_LARGE, "PDF_TOO_LARGE");
        }
        Path path = copyPdf(file);
        String language = workspace.sourceLanguage;
        queue(workspace, DemoDtos.SourceType.PDF, "PDF document", path, () -> pdfPassages(path, language));
        return view(workspace);
    }

    @Override
    public DemoDtos.WorkspaceView ingestYouTube(HttpSession session, DemoDtos.YouTubeRequest request,
                                                HttpServletRequest servletRequest) {
        requireIngestion(servletRequest);
        DemoWorkspace workspace = requireWorkspace(session);
        String videoId;
        try { videoId = youTubeUrlPolicy.parse(request.url()).videoId(); }
        catch (IllegalArgumentException exception) { throw problem(HttpStatus.BAD_REQUEST, "INVALID_YOUTUBE_URL"); }
        queue(workspace, DemoDtos.SourceType.YOUTUBE, "YouTube video", videoId,
                () -> youTubePassages(videoId, workspace.sourceLanguage));
        return view(workspace);
    }

    @Override
    public DemoDtos.WorkspaceView removeSource(HttpSession session, DemoDtos.SourceType type) {
        DemoWorkspace workspace = requireWorkspace(session);
        synchronized (workspace) {
            DemoWorkspace.Source source = workspace.sources.remove(type);
            Future<?> task = workspace.tasks.remove(type);
            if (task != null) task.cancel(true);
            if (source != null && source.retryInput() instanceof Path path) delete(path);
            workspace.ingestionInFlight = workspace.tasks.values().stream().anyMatch(taskValue -> !taskValue.isDone());
            workspace.corpusRevision++;
            clearLookups(workspace);
            return viewLocked(workspace);
        }
    }

    @Override
    public DemoDtos.WorkspaceView retrySource(HttpSession session, DemoDtos.SourceType type,
                                              HttpServletRequest servletRequest) {
        requireIngestion(servletRequest);
        DemoWorkspace workspace = requireWorkspace(session);
        Object retry;
        synchronized (workspace) {
            DemoWorkspace.Source source = workspace.sources.get(type);
            if (source == null || source.state() != DemoDtos.SourceState.FAILED || source.retryInput() == null) {
                throw problem(HttpStatus.CONFLICT, "DEMO_SOURCE_NOT_RETRYABLE");
            }
            if (source.retryExpiresAt() == null || !Instant.now().isBefore(source.retryExpiresAt())) {
                if (source.retryInput() instanceof Path path) delete(path);
                workspace.sources.put(type, new DemoWorkspace.Source(source.reference(), source.revision(),
                        source.type(), source.label(), source.state(), source.safeErrorCode(),
                        source.characterCount(), source.passages(), null, null));
                throw problem(HttpStatus.CONFLICT, "DEMO_SOURCE_NOT_RETRYABLE");
            }
            retry = source.retryInput();
        }
        return switch (type) {
            case PASTED_TEXT -> {
                queue(workspace, type, "Pasted text", retry,
                        () -> pastedPassages((String) retry, workspace.sourceLanguage));
                yield view(workspace);
            }
            case PDF -> {
                String language = workspace.sourceLanguage;
                queue(workspace, type, "PDF document", retry, () -> pdfPassages((Path) retry, language));
                yield view(workspace);
            }
            case YOUTUBE -> {
                queue(workspace, type, "YouTube video", retry,
                        () -> youTubePassages((String) retry, workspace.sourceLanguage));
                yield view(workspace);
            }
        };
    }

    @Override
    public DemoDtos.LookupView lookup(HttpSession session, DemoDtos.LookupRequest request) {
        DemoWorkspace workspace = requireWorkspace(session);
        String normalized = textNormalizer.normalizeForSearch(request.term());
        if (normalized.isBlank() || codePoints(normalized) > configuration.getSearch().getMaxTermCharacters()) {
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_LOOKUP_TERM");
        }
        LanguagePairDto languagePair;
        long corpusRevision;
        String cacheKey;
        synchronized (workspace) {
            pruneTimes(workspace.lookupBurst, Duration.ofMinutes(1));
            languagePair = pair(workspace.sourceLanguage, workspace.targetLanguage);
            corpusRevision = workspace.corpusRevision;
            cacheKey = normalized + '\u0000' + workspace.sourceLanguage + '\u0000'
                    + workspace.targetLanguage + '\u0000' + corpusRevision;
            DemoWorkspace.Lookup cached = workspace.lookupCache.get(cacheKey);
            if (cached != null) {
                workspace.latestLookup = withCached(cached, true);
                return lookupView(workspace.latestLookup);
            }
            if (!workspace.uniqueTerms.contains(normalized)
                    && workspace.uniqueTerms.size() >= configuration.getDemo().getMaxUniqueLookups()) {
                throw problem(HttpStatus.TOO_MANY_REQUESTS, "LOOKUP_LIMIT_REACHED");
            }
            if (workspace.lookupBurst.size() >= configuration.getDemo().getLookupBurstPerMinute()) {
                throw new DemoException(HttpStatus.TOO_MANY_REQUESTS, "DEMO_RATE_LIMITED", 60, null);
            }
            workspace.lookupBurst.addLast(Instant.now());
            workspace.uniqueTerms.add(normalized);
        }

        Optional<DictionaryEntryDto> result = dictionaryProvider.lookup(
                new DictionaryLookupRequestDto(request.term(), languagePair));
        DictionaryEntryDto entry = result.orElse(null);
        List<DemoWorkspace.Context> contexts = contexts(workspace, entry, normalized);
        DemoWorkspace.Lookup lookup = new DemoWorkspace.Lookup(randomReference(), cacheKey, normalized,
                entry, contexts, Instant.now(), false);
        synchronized (workspace) {
            if (workspace.corpusRevision != corpusRevision
                    || !workspace.sourceLanguage.equals(languagePair.sourceLanguage())
                    || !workspace.targetLanguage.equals(languagePair.definitionLanguage())) {
                throw problem(HttpStatus.CONFLICT, "DEMO_LOOKUP_STALE");
            }
            workspace.lookupCache.put(cacheKey, lookup);
            workspace.latestLookup = lookup;
            return lookupView(lookup);
        }
    }

    @Override
    public DemoDtos.CardMutation saveCard(HttpSession session, DemoDtos.SaveCardRequest request) {
        DemoWorkspace workspace = requireWorkspace(session);
        synchronized (workspace) {
            DemoWorkspace.Lookup lookup = workspace.lookupCache.values().stream()
                    .filter(value -> value.reference().equals(request.lookupReference())).findFirst()
                    .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "DEMO_LOOKUP_NOT_FOUND"));
            if (lookup.entry() == null) throw problem(HttpStatus.CONFLICT, "DEFINITION_NOT_FOUND");
            String selection = request.kind() == DemoDtos.CardKind.CONTEXT
                    ? Objects.requireNonNullElse(request.selectionReference(), "") : "definition";
            String originKey = lookup.cacheKey() + ':' + request.kind() + ':' + selection;
            DemoWorkspace.Card existing = workspace.cards.values().stream()
                    .filter(card -> card.originKey().equals(originKey)).findFirst().orElse(null);
            if (existing != null) return new DemoDtos.CardMutation(cardView(existing), true);
            if (workspace.cards.size() >= configuration.getDemo().getMaxCards()) {
                throw problem(HttpStatus.CONFLICT, "DEMO_DECK_FULL");
            }
            DemoWorkspace.Context context = null;
            if (request.kind() == DemoDtos.CardKind.CONTEXT) {
                context = lookup.contexts().stream().filter(value -> value.reference().equals(selection)).findFirst()
                        .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "DEMO_CONTEXT_NOT_FOUND"));
            }
            AnkiCardFieldsDto fields = context == null
                    ? definitionFields(lookup.entry()) : contextFields(lookup.entry(), context);
            String reference = randomReference();
            String definition = lookup.entry().senses().get(0).definition();
            DemoWorkspace.Card card = new DemoWorkspace.Card(reference, request.kind(), originKey,
                    lookup.entry().requestedTerm(), definition,
                    context == null ? "" : context.passage().text(),
                    context == null ? "" : context.source().label(),
                    workspace.sourceLanguage + " → " + workspace.targetLanguage,
                    Instant.now(), fields);
            workspace.cards.put(reference, card);
            workspace.deckRevision++;
            invalidateExport(workspace);
            return new DemoDtos.CardMutation(cardView(card), false);
        }
    }

    @Override
    public DemoDtos.CardRemoval removeCard(HttpSession session, String cardReference) {
        DemoWorkspace workspace = requireWorkspace(session);
        synchronized (workspace) {
            int index = new ArrayList<>(workspace.cards.keySet()).indexOf(cardReference);
            DemoWorkspace.Card card = workspace.cards.remove(cardReference);
            if (card == null) throw problem(HttpStatus.NOT_FOUND, "DEMO_CARD_NOT_FOUND");
            String token = randomReference();
            Instant expiresAt = Instant.now().plus(UNDO_LIFETIME);
            workspace.tombstones.put(cardReference, new DemoWorkspace.Tombstone(card, token, expiresAt, index));
            workspace.deckRevision++;
            invalidateExport(workspace);
            return new DemoDtos.CardRemoval(token, expiresAt);
        }
    }

    @Override
    public DemoDtos.CardMutation undoCard(HttpSession session, String cardReference, String undoToken) {
        DemoWorkspace workspace = requireWorkspace(session);
        synchronized (workspace) {
            DemoWorkspace.Tombstone tombstone = workspace.tombstones.get(cardReference);
            if (tombstone == null || !tombstone.token().equals(undoToken)
                    || !Instant.now().isBefore(tombstone.expiresAt())) {
                throw problem(HttpStatus.NOT_FOUND, "DEMO_UNDO_EXPIRED");
            }
            if (workspace.cards.size() >= configuration.getDemo().getMaxCards()) {
                throw problem(HttpStatus.CONFLICT, "DEMO_DECK_FULL");
            }
            restoreAt(workspace.cards, tombstone.card(), tombstone.originalIndex());
            workspace.tombstones.remove(cardReference);
            workspace.deckRevision++;
            invalidateExport(workspace);
            return new DemoDtos.CardMutation(cardView(tombstone.card()), false);
        }
    }

    @Override
    public DemoDtos.ExportView export(HttpSession session, HttpServletRequest request) {
        DemoWorkspace workspace = requireWorkspace(session);
        synchronized (workspace) {
            if (workspace.cards.isEmpty()) throw problem(HttpStatus.CONFLICT, "DEMO_DECK_EMPTY");
            if (workspace.cachedExport != null && workspace.cachedExport.deckRevision() == workspace.deckRevision
                    && Files.isRegularFile(workspace.cachedExport.path())) {
                return new DemoDtos.ExportView(workspace.cachedExport.reference(), true);
            }
            if (workspace.exportGenerations >= configuration.getDemo().getMaxExportGenerations()) {
                throw problem(HttpStatus.TOO_MANY_REQUESTS, "DEMO_EXPORT_LIMIT_REACHED");
            }
        }
        rateLimiter.require(request, "export", configuration.getDemo().getExportGenerationsPerIpMinute(),
                Duration.ofMinutes(1));
        if (!exportSemaphore.tryAcquire()) throw new DemoException(HttpStatus.SERVICE_UNAVAILABLE,
                "DEMO_BUSY", 5, null);
        try {
            Map<String, Object> snapshot;
            long revision;
            synchronized (workspace) {
                revision = workspace.deckRevision;
                snapshot = exportSnapshot(workspace);
            }
            ExportedDeckDto exported = deckExporter.export(snapshot);
            synchronized (workspace) {
                if (workspace.deckRevision != revision) {
                    deleteTree(exported.path().getParent());
                    throw problem(HttpStatus.CONFLICT, "DEMO_EXPORT_STALE");
                }
                invalidateExport(workspace);
                String reference = randomReference();
                workspace.cachedExport = new DemoWorkspace.CachedExport(
                        reference, revision, exported.path(), exported.byteSize());
                workspace.exportGenerations++;
                return new DemoDtos.ExportView(reference, false);
            }
        } finally {
            exportSemaphore.release();
        }
    }

    @Override
    public DemoDownload download(HttpSession session, String exportReference) {
        DemoWorkspace workspace = requireWorkspace(session);
        synchronized (workspace) {
            DemoWorkspace.CachedExport cached = workspace.cachedExport;
            if (cached == null || !cached.reference().equals(exportReference)
                    || !Files.isRegularFile(cached.path())) {
                throw problem(HttpStatus.NOT_FOUND, "DEMO_EXPORT_NOT_FOUND");
            }
            Resource resource = new FileSystemResource(cached.path());
            return new DemoDownload(resource, cached.size());
        }
    }

    @PreDestroy
    void shutdown() {
        ingestionExecutor.shutdownNow();
    }

    private void queue(DemoWorkspace workspace, DemoDtos.SourceType type, String label,
                       Object retryInput, PassageSupplier supplier) {
        long revision;
        synchronized (workspace) {
            if (workspace.ingestionInFlight) {
                Future<?> replacing = workspace.tasks.get(type);
                if (replacing == null) throw problem(HttpStatus.CONFLICT, "DEMO_SOURCE_LIMIT");
                // Same-slot replacement deliberately invalidates the old revision; the
                // old worker may finish, but its snapshot can no longer commit.
                replacing.cancel(true);
                workspace.tasks.remove(type);
                workspace.ingestionInFlight = false;
            }
            DemoWorkspace.Source prior = workspace.sources.get(type);
            if (prior != null && prior.retryInput() instanceof Path path && !path.equals(retryInput)) delete(path);
            revision = prior == null ? 1 : prior.revision() + 1;
            workspace.sources.put(type, new DemoWorkspace.Source(randomReference(), revision, type, label,
                    DemoDtos.SourceState.QUEUED, null, 0, List.of(), retryInput, null));
            workspace.ingestionInFlight = true;
            workspace.corpusRevision++;
            clearLookups(workspace);
        }
        try {
            Future<?> future = ingestionExecutor.submit(() -> runIngestion(workspace, type, revision, supplier));
            synchronized (workspace) { workspace.tasks.put(type, future); }
        } catch (RuntimeException exception) {
            synchronized (workspace) {
                workspace.ingestionInFlight = false;
                failCurrent(workspace, type, revision, "DEMO_BUSY");
            }
            throw new DemoException(HttpStatus.SERVICE_UNAVAILABLE, "DEMO_BUSY", 5, exception);
        }
    }

    private void runIngestion(DemoWorkspace workspace, DemoDtos.SourceType type, long revision,
                              PassageSupplier supplier) {
        markProcessing(workspace, type, revision);
        try {
            PassageResult result = supplier.get();
            synchronized (workspace) {
                DemoWorkspace.Source current = workspace.sources.get(type);
                if (current == null || current.revision() != revision) {
                    result.cleanup();
                    return;
                }
                int otherCharacters = workspace.sources.values().stream()
                        .filter(source -> source.type() != type && source.state() == DemoDtos.SourceState.READY)
                        .mapToInt(DemoWorkspace.Source::characterCount).sum();
                if ((long) otherCharacters + result.characterCount() >
                        configuration.getDemo().getWorkspaceMaxIndexedCharacters()) {
                    throw problem(HttpStatus.PAYLOAD_TOO_LARGE, "DEMO_TOTAL_TEXT_LIMIT");
                }
                workspace.sources.put(type, new DemoWorkspace.Source(current.reference(), revision, type,
                        result.label() == null ? current.label() : result.label(), DemoDtos.SourceState.READY,
                        null, result.characterCount(), result.passages(), null, null));
                workspace.corpusRevision++;
                clearLookups(workspace);
                result.cleanup();
                log.info("Anonymous demo source ready workspaceId={} sourceType={} characterCount={} passageCount={}",
                        workspace.id, type, result.characterCount(), result.passages().size());
            }
        } catch (DemoException exception) {
            synchronized (workspace) { failCurrent(workspace, type, revision, exception.getProblemCode()); }
        } catch (RuntimeException exception) {
            synchronized (workspace) { failCurrent(workspace, type, revision, safeIngestionCode(type, exception)); }
        } finally {
            synchronized (workspace) {
                DemoWorkspace.Source current = workspace.sources.get(type);
                if (current == null || current.revision() == revision) {
                    workspace.tasks.remove(type);
                    workspace.ingestionInFlight = workspace.tasks.values().stream()
                            .anyMatch(task -> !task.isDone());
                }
            }
        }
    }

    private PassageResult pastedPassages(String raw, String language) {
        String display = textNormalizer.normalizeDisplay(raw);
        List<PassageDraftDto> drafts = passageSegmenter.segment(display, new LanguageTag(language));
        return passageResult("Pasted text", drafts, Map.of(), null);
    }

    private PassageResult pdfPassages(Path path, String language) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            if (document.isEncrypted()) throw problem(HttpStatus.UNPROCESSABLE_ENTITY, "ENCRYPTED_DOCUMENT");
            if (document.getNumberOfPages() < 1) throw problem(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PDF");
            if (document.getNumberOfPages() > configuration.getDemo().getPdfMaxPages()) {
                throw problem(HttpStatus.PAYLOAD_TOO_LARGE, "PDF_TOO_MANY_PAGES");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<PassageDraftDto> drafts = new ArrayList<>();
            int characters = 0;
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                if (Thread.currentThread().isInterrupted()) throw problem(HttpStatus.SERVICE_UNAVAILABLE, "DEMO_BUSY");
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String display = textNormalizer.normalizeDisplay(stripper.getText(document));
                characters += codePoints(display);
                if (characters > configuration.getDemo().getPdfMaxExtractedCharacters()) {
                    throw problem(HttpStatus.PAYLOAD_TOO_LARGE, "PDF_TEXT_TOO_LARGE");
                }
                for (PassageDraftDto draft : passageSegmenter.segment(display,
                        new LanguageTag(language))) {
                    Map<String, Object> location = new LinkedHashMap<>(draft.location());
                    location.put("pageNumber", page);
                    drafts.add(new PassageDraftDto(draft.displayText(), location, draft.tokenCount()));
                }
            }
            if (drafts.isEmpty()) throw problem(HttpStatus.UNPROCESSABLE_ENTITY, "PDF_NEEDS_OCR");
            return passageResult("PDF document", drafts, Map.of(), path);
        } catch (InvalidPasswordException exception) {
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, "ENCRYPTED_DOCUMENT");
        } catch (IOException exception) {
            throw new DemoException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PDF", null, exception);
        }
    }

    private PassageResult youTubePassages(String videoId, String language) {
        youTubeMetadataProbe.requireAllowedDuration(videoId);
        YouTubeSubtitleResultDto result = youTubeSubtitleProvider.fetch(
                videoId, language, configuration.getYoutube().isAutomaticCaptionFallback());
        if (result.durationMs() != null
                && result.durationMs() > configuration.getDemo().getYoutubeMaxDuration().toMillis()) {
            throw problem(HttpStatus.PAYLOAD_TOO_LARGE, "YOUTUBE_TOO_LONG");
        }
        List<PassageDraftDto> drafts = youTubePassageBuilder.build(videoId, result.channelName(), result.cues());
        return passageResult(result.title(), drafts, Map.of(), null);
    }

    private PassageResult passageResult(String label, List<PassageDraftDto> drafts,
                                        Map<String, Object> sharedLocation, Path cleanup) {
        List<DemoWorkspace.Passage> passages = new ArrayList<>();
        int characters = 0;
        for (int index = 0; index < drafts.size(); index++) {
            PassageDraftDto draft = drafts.get(index);
            characters += codePoints(draft.displayText());
            Map<String, Object> location = new LinkedHashMap<>(sharedLocation);
            location.putAll(draft.location());
            passages.add(new DemoWorkspace.Passage(UUID.randomUUID(), index, draft.displayText(),
                    Map.copyOf(location)));
        }
        int total = characters;
        return new PassageResult(label, List.copyOf(passages), total, cleanup);
    }

    private List<DemoWorkspace.Context> contexts(DemoWorkspace workspace, DictionaryEntryDto entry,
                                                  String fallbackTerm) {
        List<String> terms = entry == null ? List.of(fallbackTerm) : entry.searchTerms();
        List<String> normalizedTerms = terms.stream().map(textNormalizer::normalizeForSearch)
                .filter(term -> !term.isBlank())
                .filter(term -> codePoints(term) <= configuration.getSearch().getMaxTermCharacters())
                .distinct().limit(configuration.getSearch().getMaxDictionarySearchTerms()).toList();
        EnumMap<DemoDtos.SourceType, List<DemoWorkspace.Context>> perSource = new EnumMap<>(DemoDtos.SourceType.class);
        synchronized (workspace) {
            workspace.sources.values().stream().filter(source -> source.state() == DemoDtos.SourceState.READY)
                    .forEach(source -> {
                        List<DemoWorkspace.Context> matches = source.passages().stream()
                                .filter(passage -> containsTerm(textNormalizer.normalizeForSearch(passage.text()), normalizedTerms))
                                .sorted(Comparator.comparingInt(passage -> Math.abs(
                                        codePoints(passage.text()) - configuration.getSearch().getIdealPassageCharacters())))
                                .limit(configuration.getSearch().getLookupMaxResultsPerSource())
                                .map(passage -> new DemoWorkspace.Context(randomReference(), passage, source)).toList();
                        if (!matches.isEmpty()) perSource.put(source.type(), matches);
                    });
        }
        List<DemoWorkspace.Context> diversified = new ArrayList<>();
        for (int round = 0; diversified.size() < configuration.getSearch().getLookupResults(); round++) {
            boolean added = false;
            for (DemoDtos.SourceType type : DemoDtos.SourceType.values()) {
                List<DemoWorkspace.Context> values = perSource.getOrDefault(type, List.of());
                if (round < values.size()) {
                    diversified.add(values.get(round));
                    added = true;
                    if (diversified.size() == configuration.getSearch().getLookupResults()) break;
                }
            }
            if (!added) break;
        }
        return List.copyOf(diversified);
    }

    private boolean containsTerm(String text, List<String> terms) {
        return terms.stream().anyMatch(term -> Pattern.compile(
                "(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])").matcher(text).find());
    }

    private AnkiCardFieldsDto contextFields(DictionaryEntryDto entry, DemoWorkspace.Context context) {
        NumberedContextExampleDto numbered = new NumberedContextExampleDto(
                1, context.passage().id(), UUID.fromString(context.source().reference()), sourceKind(context.source().type()),
                context.source().label(), entry.languagePair().sourceLanguage(), context.passage().ordinal(),
                context.passage().text(), context.passage().location());
        return cardFieldMapper.map(entry, numbered);
    }

    private AnkiCardFieldsDto definitionFields(DictionaryEntryDto entry) {
        DictionarySenseDto primary = entry.senses().get(0);
        String details = entry.senses().stream().skip(1)
                .map(sense -> sense.partOfSpeech() + ": " + sense.definition())
                .reduce((left, right) -> left + "\n" + right).orElse("");
        return new AnkiCardFieldsDto(AnkiCardFieldsDto.CURRENT_SCHEMA_VERSION,
                AnkiCardFieldsDto.NOTE_TYPE_NAME, AnkiCardFieldsDto.TEMPLATE_VERSION,
                entry.requestedTerm(), "", entry.requestedTerm(), "", "",
                primary.definition(), details, "", primary.partOfSpeech(), "", "", "", "", "",
                entry.languagePair().sourceLanguage() + " → " + entry.languagePair().definitionLanguage(),
                "", "", "", "", "", "1");
    }

    private Map<String, Object> exportSnapshot(DemoWorkspace workspace) {
        List<Map<String, Object>> cards = workspace.cards.values().stream().map(card -> {
            LinkedHashMap<String, Object> anki = new LinkedHashMap<>();
            anki.put("schemaVersion", 1);
            anki.put("noteTypeName", AnkiCardFieldsDto.NOTE_TYPE_NAME);
            anki.put("templateVersion", AnkiCardFieldsDto.TEMPLATE_VERSION);
            anki.put("fields", card.fields().toFieldMap());
            LinkedHashMap<String, Object> value = new LinkedHashMap<>();
            value.put("stableId", card.reference());
            value.put("noteType", "BASIC_CONTEXT");
            value.put("anki", Collections.unmodifiableMap(anki));
            return Collections.unmodifiableMap(value);
        }).toList();
        LinkedHashMap<String, Object> deck = new LinkedHashMap<>();
        deck.put("stableId", workspace.stableDeckId.toString());
        deck.put("name", "Loquela Demo");
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("exportId", UUID.randomUUID().toString());
        snapshot.put("deck", Collections.unmodifiableMap(deck));
        snapshot.put("templateVersion", AnkiCardFieldsDto.TEMPLATE_VERSION);
        snapshot.put("cards", cards);
        return Collections.unmodifiableMap(snapshot);
    }

    private DemoWorkspace requireWorkspace(HttpSession session) {
        requireEnabled();
        DemoWorkspace workspace = resolveOptional(session);
        if (workspace == null) {
            if (session != null) session.removeAttribute(SESSION_ATTRIBUTE);
            throw problem(HttpStatus.NOT_FOUND, "DEMO_EXPIRED");
        }
        return workspace;
    }

    private DemoWorkspace resolveOptional(HttpSession session) {
        UUID id = sessionWorkspaceId(session);
        return id == null ? null : store.resolve(id, Instant.now());
    }

    private UUID sessionWorkspaceId(HttpSession session) {
        if (session == null) return null;
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        try { return value instanceof String text ? UUID.fromString(text) : null; }
        catch (IllegalArgumentException exception) { return null; }
    }

    private void requireEnabled() {
        if (!configuration.getDemo().isEnabled()) {
            throw problem(HttpStatus.SERVICE_UNAVAILABLE, "DEMO_UNAVAILABLE");
        }
    }

    private void requireIngestion(HttpServletRequest request) {
        requireEnabled();
        if (!configuration.getDemo().isIngestionEnabled()) {
            throw problem(HttpStatus.SERVICE_UNAVAILABLE, "DEMO_INGESTION_DISABLED");
        }
        botVerificationService.verify(request);
        rateLimiter.require(request, "ingestion", configuration.getDemo().getIngestionsPerIpHour(),
                Duration.ofHours(1));
    }

    private LanguagePairDto pair(String source, String target) {
        try { return new LanguagePairDto(source, target); }
        catch (IllegalArgumentException exception) { throw problem(HttpStatus.BAD_REQUEST, "UNSUPPORTED_LANGUAGE_PAIR"); }
    }

    private void requireSupportedPair(LanguagePairDto pair) {
        String key = pair.sourceLanguage() + ':' + pair.definitionLanguage();
        if (!configuration.getLanguages().getSupportedPairs().contains(key) || !dictionaryProvider.supports(pair)) {
            throw problem(HttpStatus.BAD_REQUEST, "UNSUPPORTED_LANGUAGE_PAIR");
        }
    }

    private DemoDtos.WorkspaceView view(DemoWorkspace workspace) {
        synchronized (workspace) { return viewLocked(workspace); }
    }

    private DemoDtos.WorkspaceView viewLocked(DemoWorkspace workspace) {
        int characters = workspace.sources.values().stream()
                .filter(source -> source.state() == DemoDtos.SourceState.READY)
                .mapToInt(DemoWorkspace.Source::characterCount).sum();
        return new DemoDtos.WorkspaceView(workspace.createdAt,
                workspace.lastAccessedAt.plus(configuration.getDemo().getInactivityTimeout()),
                workspace.createdAt.plus(configuration.getDemo().getAbsoluteLifetime()),
                workspace.sourceLanguage, workspace.targetLanguage, workspace.corpusRevision,
                workspace.sources.values().stream().map(this::sourceView).toList(), characters,
                workspace.latestLookup == null ? null : lookupView(workspace.latestLookup),
                workspace.cards.values().stream().map(this::cardView).toList(), workspace.deckRevision,
                workspace.cachedExport != null && workspace.cachedExport.deckRevision() == workspace.deckRevision);
    }

    private DemoDtos.SourceView sourceView(DemoWorkspace.Source source) {
        return new DemoDtos.SourceView(source.type(), source.reference(), source.label(), source.state(),
                source.safeErrorCode(), youtubeVideoId(source), source.characterCount(), source.passages().size());
    }

    private String youtubeVideoId(DemoWorkspace.Source source) {
        if (source.type() != DemoDtos.SourceType.YOUTUBE) return null;
        return source.passages().stream()
                .map(DemoWorkspace.Passage::location)
                .map(location -> location.get("videoId"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(videoId -> YOUTUBE_VIDEO_ID.matcher(videoId).matches())
                .findFirst()
                .orElse(null);
    }

    private DemoDtos.LookupView lookupView(DemoWorkspace.Lookup lookup) {
        List<DemoDtos.DefinitionView> definitions = lookup.entry() == null ? List.of()
                : lookup.entry().senses().stream().map(sense ->
                new DemoDtos.DefinitionView(sense.partOfSpeech(), sense.definition())).toList();
        return new DemoDtos.LookupView(lookup.reference(), lookup.normalizedTerm(), definitions,
                lookup.contexts().stream().map(context -> new DemoDtos.ContextView(context.reference(),
                        context.passage().text(), context.source().label(), context.source().type(),
                        context.passage().location())).toList(), lookup.cached());
    }

    private DemoDtos.CardView cardView(DemoWorkspace.Card card) {
        return new DemoDtos.CardView(card.reference(), card.kind(), card.term(), card.definition(),
                card.example(), card.sourceLabel(), card.languagePair(), card.createdAt());
    }

    private DemoWorkspace.Lookup withCached(DemoWorkspace.Lookup lookup, boolean cached) {
        return new DemoWorkspace.Lookup(lookup.reference(), lookup.cacheKey(), lookup.normalizedTerm(),
                lookup.entry(), lookup.contexts(), lookup.createdAt(), cached);
    }

    private void markProcessing(DemoWorkspace workspace, DemoDtos.SourceType type, long revision) {
        synchronized (workspace) {
            DemoWorkspace.Source current = workspace.sources.get(type);
            if (current != null && current.revision() == revision) {
                workspace.sources.put(type, new DemoWorkspace.Source(current.reference(), revision, type,
                        current.label(), DemoDtos.SourceState.PROCESSING, null, 0, List.of(),
                        current.retryInput(), null));
            }
        }
    }

    private void failCurrent(DemoWorkspace workspace, DemoDtos.SourceType type, long revision, String code) {
        DemoWorkspace.Source current = workspace.sources.get(type);
        if (current != null && current.revision() == revision) {
            boolean retryable = retryableIngestion(code);
            if (!retryable && current.retryInput() instanceof Path path) delete(path);
            workspace.sources.put(type, new DemoWorkspace.Source(current.reference(), revision, type,
                    current.label(), DemoDtos.SourceState.FAILED, code, 0, List.of(),
                    retryable ? current.retryInput() : null,
                    retryable ? Instant.now().plus(Duration.ofMinutes(10)) : null));
            log.info("Anonymous demo source failed workspaceId={} sourceType={} safeErrorCode={}",
                    workspace.id, type, code);
        }
    }

    private void clearSources(DemoWorkspace workspace) {
        workspace.tasks.values().forEach(task -> task.cancel(true));
        workspace.sources.values().forEach(source -> {
            if (source.retryInput() instanceof Path path) delete(path);
        });
        workspace.tasks.clear();
        workspace.sources.clear();
        workspace.ingestionInFlight = false;
    }

    private void clearLookups(DemoWorkspace workspace) {
        workspace.lookupCache.clear();
        workspace.latestLookup = null;
    }

    private void invalidateExport(DemoWorkspace workspace) {
        if (workspace.cachedExport != null) deleteTree(workspace.cachedExport.path().getParent());
        workspace.cachedExport = null;
    }

    private Path copyPdf(MultipartFile file) {
        Path path = null;
        try {
            byte[] header;
            try (var input = file.getInputStream()) {
                header = input.readNBytes(8);
            }
            String signature = new String(header, java.nio.charset.StandardCharsets.US_ASCII);
            if (!PDF_SIGNATURE.matcher(signature).matches()) {
                throw problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MEDIA_TYPE_MISMATCH");
            }
            path = Files.createTempFile("loquela-demo-pdf-", ".pdf");
            file.transferTo(path);
            return path;
        } catch (DemoException exception) {
            delete(path);
            throw exception;
        } catch (IOException exception) {
            delete(path);
            throw new DemoException(HttpStatus.SERVICE_UNAVAILABLE, "DEMO_BUSY", 5, exception);
        }
    }

    private String safeIngestionCode(DemoDtos.SourceType type, RuntimeException exception) {
        String message = exception.getMessage();
        if (message != null && message.matches("[A-Z0-9_]{3,80}")) return message;
        return switch (type) {
            case PDF -> "INVALID_PDF";
            case YOUTUBE -> "YOUTUBE_VIDEO_UNAVAILABLE";
            case PASTED_TEXT -> "DEMO_INGESTION_FAILED";
        };
    }

    private boolean retryableIngestion(String code) {
        return Set.of("DEMO_BUSY", "DEMO_INGESTION_FAILED", "YOUTUBE_PROVIDER_TEMPORARY_FAILURE",
                "YTDLP_EXECUTION_FAILED", "YOUTUBE_CAPTION_IO_FAILED").contains(code);
    }

    private SourceDocumentEntity.Kind sourceKind(DemoDtos.SourceType type) {
        return switch (type) {
            case YOUTUBE -> SourceDocumentEntity.Kind.YOUTUBE_VIDEO;
            case PDF -> SourceDocumentEntity.Kind.PDF;
            case PASTED_TEXT -> SourceDocumentEntity.Kind.PASTED_TEXT;
        };
    }

    private void restoreAt(LinkedHashMap<String, DemoWorkspace.Card> cards, DemoWorkspace.Card card, int index) {
        LinkedHashMap<String, DemoWorkspace.Card> restored = new LinkedHashMap<>();
        int current = 0;
        boolean added = false;
        for (Map.Entry<String, DemoWorkspace.Card> entry : cards.entrySet()) {
            if (!added && current++ == index) {
                restored.put(card.reference(), card);
                added = true;
            }
            restored.put(entry.getKey(), entry.getValue());
        }
        if (!added) restored.put(card.reference(), card);
        cards.clear();
        cards.putAll(restored);
    }

    private void pruneTimes(java.util.ArrayDeque<Instant> values, Duration window) {
        Instant cutoff = Instant.now().minus(window);
        while (!values.isEmpty() && !values.peekFirst().isAfter(cutoff)) values.removeFirst();
    }

    private String randomReference() { return UUID.randomUUID().toString(); }
    private int codePoints(String text) { return text.codePointCount(0, text.length()); }
    private DemoException problem(HttpStatus status, String code) { return new DemoException(status, code); }
    private void delete(Path path) { if (path != null) try { Files.deleteIfExists(path); } catch (IOException ignored) { } }
    private void deleteTree(Path root) {
        if (root == null) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::delete);
        } catch (IOException ignored) { }
    }

    @FunctionalInterface
    private interface PassageSupplier { PassageResult get(); }
    private record PassageResult(String label, List<DemoWorkspace.Passage> passages,
                                 int characterCount, Path cleanupPath) {
        void cleanup() {
            if (cleanupPath != null) try { Files.deleteIfExists(cleanupPath); } catch (IOException ignored) { }
        }
    }
}
