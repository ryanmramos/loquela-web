import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { DeckApi } from '@api/api-adapters/deck-api';

import { DecksPage } from './decks-page';

describe('DecksPage', () => {
  const list = vi.fn(() =>
    of({
      items: [
        { reference: 'deck-1', name: 'Spanish Core', themeColor: 'GREEN' as const, cardCount: 2 },
      ],
      page: 0,
      pageSize: 20,
      totalItems: 1,
      totalPages: 1,
    }),
  );
  const exports = vi.fn(() =>
    of({ items: [], page: 0, pageSize: 20, totalItems: 0, totalPages: 0 }),
  );
  const create = vi.fn(() => of({ reference: 'deck-2' }));
  const update = vi.fn(() => of({ reference: 'deck-1' }));
  const deleteDeck = vi.fn(() => of(undefined));
  const requestExport = vi.fn(() =>
    of({
      reference: 'export-1',
      deckReference: 'deck-1',
      deckName: 'Spanish Core',
      cardCount: 2,
      state: 'QUEUED' as const,
    }),
  );

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [DecksPage],
      providers: [
        provideRouter([]),
        {
          provide: DeckApi,
          useValue: {
            list,
            exports,
            create,
            update,
            delete: deleteDeck,
            export: requestExport,
            downloadUrl: (reference: string) => `/download/${reference}`,
          },
        },
      ],
    });
  });

  it('loads all decks and export history from durable server state', () => {
    const page = TestBed.createComponent(DecksPage).componentInstance;

    expect(page.decks()).toHaveLength(1);
    expect(page.exports()).toEqual([]);
    expect(list).toHaveBeenCalledOnce();
    expect(exports).toHaveBeenCalledOnce();
  });

  it('persists both a renamed deck and its selected theme color', () => {
    const page = TestBed.createComponent(DecksPage).componentInstance;
    page.edit(page.decks()[0]);
    page.form.setValue({ name: 'Reading', themeColor: 'PURPLE' });

    page.save();

    expect(update).toHaveBeenCalledWith('deck-1', {
      name: 'Reading',
      themeColor: 'PURPLE',
    });
  });

  it('renders every supported theme color as a labelled radio option', () => {
    const fixture = TestBed.createComponent(DecksPage);
    fixture.componentInstance.edit(fixture.componentInstance.decks()[0]);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const options = Array.from(element.querySelectorAll<HTMLLabelElement>('.color-option'));

    expect(options.map((option) => option.textContent?.trim())).toEqual([
      'Green',
      'Orange',
      'Blue',
      'Teal',
      'Purple',
    ]);
    expect(
      options.every((option) => option.querySelector<HTMLInputElement>('input[type="radio"]')),
    ).toBe(true);
  });

  it('queues an immutable export and refreshes visible history from the server', () => {
    const page = TestBed.createComponent(DecksPage).componentInstance;

    page.requestExport(page.decks()[0]);

    expect(requestExport).toHaveBeenCalledWith('deck-1');
    expect(exports).toHaveBeenCalledTimes(2);
  });

  it('does not queue an empty deck and exposes a useful error', () => {
    const page = TestBed.createComponent(DecksPage).componentInstance;

    page.requestExport({ reference: 'empty', name: 'Empty', cardCount: 0 });

    expect(requestExport).not.toHaveBeenCalled();
    expect(page.error()).toContain('at least one card');
  });

  it('keeps the deck visible when guarded deletion fails', () => {
    deleteDeck.mockReturnValueOnce(throwError(() => new Error('conflict')));
    vi.spyOn(window, 'confirm').mockReturnValueOnce(true);
    const page = TestBed.createComponent(DecksPage).componentInstance;

    page.delete(page.decks()[0]);

    expect(page.decks()).toHaveLength(1);
    expect(page.error()).toContain('cards');
  });
});
