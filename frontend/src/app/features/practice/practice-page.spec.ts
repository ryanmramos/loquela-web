import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { DeckApi } from '@api/api-adapters/deck-api';
import { PracticeApi, PracticeSession } from '@api/api-adapters/practice-api';

import { PracticePage } from './practice-page';

describe('PracticePage', () => {
  const decks = [
    { reference: 'deck-1', name: 'Spanish Core', themeColor: 'GREEN' as const, cardCount: 2 },
  ];
  const active = vi.fn(() => of<PracticeSession | null>(null));
  const start = vi.fn(() => of(hiddenSession()));
  const reveal = vi.fn(() => of(revealedSession()));
  const rate = vi.fn(() => of({ ...hiddenSession(), position: 2 }));
  const end = vi.fn(() => of({ ...hiddenSession(), status: 'EXPIRED' as const }));
  const list = vi.fn(() =>
    of({ items: decks, page: 0, pageSize: 20, totalItems: 1, totalPages: 1 }),
  );

  beforeEach(() => {
    vi.clearAllMocks();
    active.mockReturnValue(of(null));
    start.mockReturnValue(of(hiddenSession()));
    reveal.mockReturnValue(of(revealedSession()));
    rate.mockReturnValue(of({ ...hiddenSession(), position: 2 }));
    TestBed.configureTestingModule({
      imports: [PracticePage],
      providers: [
        provideRouter([]),
        { provide: DeckApi, useValue: { list } },
        {
          provide: PracticeApi,
          useValue: { active, start, reveal, rate, end },
        },
      ],
    });
  });

  it('offers only nonempty decks and labels the mode honestly', () => {
    const fixture = TestBed.createComponent(PracticePage);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).toContain('Nothing here changes Anki scheduling');
    expect(element.textContent).toContain('Spanish Core');

    fixture.componentInstance.start(decks[0]);

    expect(start).toHaveBeenCalledWith('deck-1');
    expect(fixture.componentInstance.session()?.reference).toBe('session-1');
  });

  it('does not render the unrevealed answer in the DOM', () => {
    active.mockReturnValueOnce(of(hiddenSession()));
    const fixture = TestBed.createComponent(PracticePage);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).toContain('sobremesa');
    expect(element.textContent).not.toContain('time talking after a meal');
    expect(element.querySelector('.answer')).toBeNull();
    expect(element.querySelector('iframe')).toBeNull();
  });

  it('reveals the answer with a distinct accessible heading', () => {
    active.mockReturnValueOnce(of(hiddenSession()));
    const fixture = TestBed.createComponent(PracticePage);

    fixture.componentInstance.reveal();
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(reveal).toHaveBeenCalledWith('session-1');
    expect(element.querySelector('#answer-title')?.textContent).toContain('Answer');
    expect(element.textContent).toContain('time talking after a meal');
    expect(element.querySelector('iframe')?.getAttribute('src')).toBe(
      'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?start=522&rel=0',
    );
    expect(element.textContent).toContain('Term appears at 8:42');
  });

  it('maps rating shortcuts but ignores key presses inside inputs', () => {
    active.mockReturnValueOnce(of(revealedSession()));
    const fixture = TestBed.createComponent(PracticePage);
    const page = fixture.componentInstance;
    const input = document.createElement('input');

    page.handleShortcut(new KeyboardEvent('keydown', { key: '3' }));
    expect(rate).toHaveBeenCalledWith('session-1', expect.any(String), 'GOT_IT');

    rate.mockClear();
    page.handleShortcut({
      defaultPrevented: false,
      target: input,
      key: '3',
      code: 'Digit3',
      preventDefault: vi.fn(),
    } as unknown as KeyboardEvent);
    expect(rate).not.toHaveBeenCalled();
  });

  it('retries an uncertain response with the same idempotency key', () => {
    active.mockReturnValueOnce(of(revealedSession()));
    rate.mockReturnValueOnce(throwError(() => new Error('network')));
    const page = TestBed.createComponent(PracticePage).componentInstance;

    page.rate('ALMOST');
    const firstRequestId = (rate.mock.calls as unknown as string[][])[0]?.[1];
    page.retryRating();

    expect(rate).toHaveBeenNthCalledWith(2, 'session-1', firstRequestId, 'ALMOST');
  });

  it('distinguishes an empty search result from an empty collection', () => {
    list.mockReturnValueOnce(
      of({ items: decks, page: 0, pageSize: 20, totalItems: 1, totalPages: 1 }),
    );
    list.mockReturnValueOnce(
      of({ items: [], page: 0, pageSize: 20, totalItems: 0, totalPages: 0 }),
    );
    const fixture = TestBed.createComponent(PracticePage);
    const page = fixture.componentInstance;

    page.deckSearch.setValue('missing');
    page.loadDecks();
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).toContain('No decks match that search');
    expect(element.textContent).toContain('Clear');
    expect(page.hasDeckSearch()).toBe(true);

    page.clearDeckSearch();
    expect(page.deckSearch.value).toBe('');
  });

  it('keeps the visual and accessible progress values in sync', () => {
    const page = TestBed.createComponent(PracticePage).componentInstance;

    expect(page.progress(hiddenSession())).toBe(0);
    expect(page.progressValue(hiddenSession())).toBe(0);
    expect(page.progress({ ...hiddenSession(), status: 'COMPLETED' })).toBe(100);
  });
});

function hiddenSession(): PracticeSession {
  return {
    reference: 'session-1',
    deckReference: 'deck-1',
    deckName: 'Spanish Core',
    status: 'ACTIVE',
    position: 1,
    totalCards: 2,
    answerRevealed: false,
    currentCard: {
      noteType: 'BASIC_CONTEXT',
      term: 'sobremesa',
    },
    expiresAt: '2026-07-31T12:00:00Z',
  };
}

function revealedSession(): PracticeSession {
  return {
    ...hiddenSession(),
    answerRevealed: true,
    currentCard: {
      noteType: 'BASIC_CONTEXT',
      term: 'sobremesa',
      definition: 'time talking after a meal',
      exampleText: 'La sobremesa se alargó.',
      sourceName: 'Madrid video',
      sourceLocator: '8:42',
      videoId: 'dQw4w9WgXcQ',
      videoStartSeconds: 522,
    },
  };
}
