import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ConfirmationService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { DemoApi, DemoWorkspace } from '@api/api-adapters/demo-api';
import { ApiProblemError } from '@core/errors/api-problem';

import { DemoPage } from './demo-page';

describe('DemoPage', () => {
  const capabilitiesValue = {
    enabled: true,
    ingestionEnabled: true,
    supportedLanguagePairs: ['es:en', 'en:es', 'es:es', 'en:en'],
    limits: {
      pdfBytes: 5_242_880,
      pdfPages: 100,
      pdfCharacters: 100_000,
      youtubeMinutes: 30,
      pastedTextCharacters: 20_000,
      totalIndexedCharacters: 250_000,
      uniqueLookups: 50,
      cards: 20,
    },
  };
  const capabilities = vi.fn(() => of(capabilitiesValue));
  const getWorkspace = vi.fn(() => of(workspace));
  const workspace: DemoWorkspace = {
    createdAt: new Date().toISOString(),
    inactivityExpiresAt: new Date(Date.now() + 7_200_000).toISOString(),
    absoluteExpiresAt: new Date(Date.now() + 28_800_000).toISOString(),
    sourceLanguage: 'es',
    targetLanguage: 'en',
    corpusRevision: 0,
    sources: [],
    indexedCharacters: 0,
    cards: [],
    deckRevision: 0,
    exportReady: false,
  };
  const start = vi.fn(() => of(workspace));
  const exportDeckRequest = vi.fn(() => of(new Blob(['anki'])));
  let confirm: ReturnType<typeof vi.spyOn>;
  const saveCard = vi.fn(() =>
    of({
      card: {
        reference: 'card-1',
        kind: 'DEFINITION' as const,
        term: 'vivir',
        definition: 'to live',
        example: '',
        sourceLabel: '',
        languagePair: 'es → en',
        createdAt: new Date().toISOString(),
      },
      alreadyAdded: false,
    }),
  );

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [DemoPage],
      providers: [
        provideRouter([]),
        ConfirmationService,
        {
          provide: DemoApi,
          useValue: {
            capabilities,
            start,
            exportDownload: exportDeckRequest,
            saveCard,
            workspace: getWorkspace,
          },
        },
      ],
    });
    confirm = vi.spyOn(TestBed.inject(ConfirmationService), 'confirm');
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('does not create a workspace merely by rendering the public page', () => {
    const fixture = TestBed.createComponent(DemoPage);
    fixture.detectChanges();

    expect(capabilities).toHaveBeenCalledOnce();
    expect(start).not.toHaveBeenCalled();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Start demo');
  });

  it('renders the compact workspace after the visitor starts', () => {
    const fixture = TestBed.createComponent(DemoPage);
    fixture.detectChanges();

    fixture.componentInstance.start();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.source-panel')).not.toBeNull();
    expect(element.querySelector('.lookup-panel')).not.toBeNull();
    expect(element.querySelector('.deck-panel')).not.toBeNull();
    expect(element.textContent).toContain('Make your first card');
  });

  it('adds experimental noindex metadata and removes it on destroy', () => {
    const fixture = TestBed.createComponent(DemoPage);
    fixture.detectChanges();
    expect(document.head.querySelector('meta[name="robots"]')?.getAttribute('content')).toBe(
      'noindex, nofollow',
    );

    fixture.destroy();
    expect(document.head.querySelector('meta[name="robots"]')).toBeNull();
  });

  it('offers only configured targets for the selected source language', () => {
    const fixture = TestBed.createComponent(DemoPage);
    fixture.detectChanges();
    fixture.componentInstance.capabilities.set({
      ...capabilitiesValue,
      supportedLanguagePairs: ['es:en', 'en:es'],
    });

    fixture.componentInstance.sourceLanguage = 'es';
    expect(fixture.componentInstance.targetLanguageOptions()).toEqual(['en']);
    fixture.componentInstance.sourceLanguage = 'en';
    fixture.componentInstance.sourceLanguageChanged();
    expect(fixture.componentInstance.targetLanguage).toBe('es');
  });

  it('starts the Anki download as soon as the export is ready', () => {
    const fixture = TestBed.createComponent(DemoPage);
    fixture.detectChanges();
    fixture.componentInstance.start();
    fixture.componentInstance.workspace.set({
      ...workspace,
      cards: [
        {
          reference: 'card-1',
          kind: 'DEFINITION',
          term: 'vivir',
          definition: 'to live',
          example: '',
          sourceLabel: '',
          languagePair: 'es → en',
          createdAt: new Date().toISOString(),
        },
      ],
    });
    const download = vi
      .spyOn(fixture.componentInstance, 'downloadDeck')
      .mockImplementation(() => undefined);

    fixture.componentInstance.exportDeck();

    expect(exportDeckRequest).toHaveBeenCalledOnce();
    expect(download).toHaveBeenCalledWith(expect.any(Blob));
    expect(fixture.componentInstance.downloadStatus()).toBe('demo.downloadStarting');
  });

  it('cleans up the download URL after the browser receives the click', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(DemoPage);
    const createObjectUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:anki-export');
    const revokeObjectUrl = vi.spyOn(URL, 'revokeObjectURL');
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    fixture.componentInstance.downloadDeck(new Blob(['anki']));

    expect(click).toHaveBeenCalledOnce();
    expect(revokeObjectUrl).not.toHaveBeenCalled();
    expect(document.querySelector('a[download="loquela-demo.apkg"]')).toBeNull();

    vi.runAllTimers();
    expect(createObjectUrl).toHaveBeenCalledOnce();
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:anki-export');
    vi.useRealTimers();
  });

  it('keeps a failed Anki download inside the demo', () => {
    exportDeckRequest.mockReturnValueOnce(
      throwError(
        () =>
          new ApiProblemError({
            type: 'about:blank',
            title: 'Service unavailable',
            status: 503,
            code: 'DEMO_BUSY',
            correlationId: 'test-correlation-id',
          }),
      ),
    );
    const fixture = TestBed.createComponent(DemoPage);
    fixture.detectChanges();
    const download = vi
      .spyOn(fixture.componentInstance, 'downloadDeck')
      .mockImplementation(() => undefined);

    fixture.componentInstance.exportDeck();

    expect(download).not.toHaveBeenCalled();
    expect(fixture.componentInstance.errorMessage()).toBe('demo.errorBusy');
  });

  it('shows a friendly error instead of an API problem code', () => {
    start.mockReturnValueOnce(
      throwError(
        () =>
          new ApiProblemError({
            type: 'about:blank',
            title: 'Bad request',
            status: 400,
            code: 'INVALID_YOUTUBE_URL',
            correlationId: 'test-correlation-id',
          }),
      ),
    );
    const fixture = TestBed.createComponent(DemoPage);
    fixture.detectChanges();

    fixture.componentInstance.start();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Choose an available YouTube video with captions.');
    expect(text).not.toContain('INVALID_YOUTUBE_URL');
  });

  it('offers a retry state when demo capabilities cannot load', () => {
    capabilities
      .mockReturnValueOnce(throwError(() => new Error('network unavailable')))
      .mockReturnValueOnce(of(capabilitiesValue));
    const fixture = TestBed.createComponent(DemoPage);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('The demo could not load');

    fixture.componentInstance.retryCapabilities();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Start demo');
    expect(text).not.toContain('That did not work');
  });

  it('explains a failed source without rendering its internal error code', () => {
    const fixture = TestBed.createComponent(DemoPage);
    fixture.detectChanges();
    fixture.componentInstance.workspace.set({
      ...workspace,
      sources: [
        {
          reference: 'source-1',
          type: 'PDF',
          label: 'Unreadable PDF',
          state: 'FAILED',
          safeErrorCode: 'PDF_NEEDS_OCR',
          characterCount: 0,
          passageCount: 0,
        },
      ],
    });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Choose a readable, unlocked PDF with selectable text.');
    expect(text).not.toContain('PDF_NEEDS_OCR');
  });

  it('uses a product confirmation dialog before clearing a populated workspace', () => {
    const fixture = TestBed.createComponent(DemoPage);
    fixture.detectChanges();
    fixture.componentInstance.workspace.set({
      ...workspace,
      cards: [
        {
          reference: 'card-1',
          kind: 'DEFINITION',
          term: 'vivir',
          definition: 'to live',
          example: '',
          sourceLabel: '',
          languagePair: 'es → en',
          createdAt: new Date().toISOString(),
        },
      ],
    });

    fixture.componentInstance.confirmExit();

    expect(confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        header: 'Exit this demo?',
        message: 'Exit and permanently clear this demo?',
      }),
    );
  });

  it('can resume polling after a background refresh fails', () => {
    vi.useFakeTimers();
    const processingWorkspace: DemoWorkspace = {
      ...workspace,
      sources: [
        {
          reference: 'source-1',
          type: 'PASTED_TEXT',
          label: 'A short passage',
          state: 'PROCESSING',
          characterCount: 100,
          passageCount: 0,
        },
      ],
    };
    start.mockReturnValueOnce(of(processingWorkspace));
    getWorkspace.mockReturnValueOnce(throwError(() => new Error('temporary network failure')));
    const fixture = TestBed.createComponent(DemoPage);
    fixture.detectChanges();

    fixture.componentInstance.start();
    vi.advanceTimersByTime(1_000);

    expect((fixture.componentInstance as unknown as { polling?: unknown }).polling).toBeUndefined();
    vi.useRealTimers();
  });

  it('creates a safe YouTube thumbnail and timestamp link only from valid video data', () => {
    const fixture = TestBed.createComponent(DemoPage);
    const page = fixture.componentInstance;
    const source = {
      reference: 'source-1',
      type: 'YOUTUBE' as const,
      label: 'Video',
      state: 'READY' as const,
      characterCount: 100,
      passageCount: 1,
      youtubeVideoId: 'dQw4w9WgXcQ',
    };
    const context = {
      reference: 'context-1',
      text: 'Una palabra en contexto.',
      sourceLabel: 'Video',
      sourceType: 'YOUTUBE' as const,
      location: { videoId: 'dQw4w9WgXcQ', playbackStartMs: 83_000 },
    };

    expect(page.youtubeThumbnail(source)).toBe('https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg');
    expect(page.youtubeContextUrl(context)).toBe(
      'https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=83s',
    );
    expect(page.contextTimestamp(context)).toBe('1:23');
    expect(page.youtubeThumbnail({ ...source, youtubeVideoId: 'not-a-video-id' })).toBeNull();
    expect(page.youtubeContextUrl({ ...context, location: { videoId: 'not-a-video-id' } })).toBeNull();
  });

  it('renders lookup matches as safe highlight segments', () => {
    const fixture = TestBed.createComponent(DemoPage);

    expect(fixture.componentInstance.highlightedContext('El Pepinillo y el pepinillo.', 'pepinillo')).toEqual([
      { text: 'El ', highlighted: false },
      { text: 'Pepinillo', highlighted: true },
      { text: ' y el ', highlighted: false },
      { text: 'pepinillo', highlighted: true },
      { text: '.', highlighted: false },
    ]);
    expect(fixture.componentInstance.highlightedContext('No regex here.', '[a-z]')).toEqual([
      { text: 'No regex here.', highlighted: false },
    ]);
  });

  it('shows a processed video title without exposing its intake placeholder', () => {
    const fixture = TestBed.createComponent(DemoPage);
    const page = fixture.componentInstance;
    const video = {
      reference: 'source-1',
      type: 'YOUTUBE' as const,
      label: 'A helpful video title',
      state: 'READY' as const,
      characterCount: 100,
      passageCount: 1,
    };

    expect(page.sourceDisplayName(video)).toBe('A helpful video title');
    expect(page.sourceDisplayName({ ...video, state: 'PROCESSING' })).toBe('Video');
    expect(page.sourceDisplayName({ ...video, label: 'YouTube video' })).toBe('Video');
  });
});
