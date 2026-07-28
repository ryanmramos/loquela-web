import { TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';

import { SourceApi, SourcePage } from '@api/api-adapters/source-api';

import { SourceListState } from './source-list-state';

describe('SourceListState', () => {
  it('keeps the server page and does not client-filter it', () => {
    const page = sourcePage();
    const sourceApi = { list: vi.fn(() => of(page)) };
    const state = createState(sourceApi);

    state.load({ page: 2, search: 'reader', status: 'READY' });

    expect(sourceApi.list).toHaveBeenCalledWith({ page: 2, search: 'reader', status: 'READY' });
    expect(state.page()).toEqual(page);
    expect(state.loadStatus()).toBe('success');
  });

  it('cancels an in-flight page when a new URL-synchronized query loads', () => {
    let cancelled = false;
    const pending = new Observable<SourcePage>(() => () => {
      cancelled = true;
    });
    const sourceApi = { list: vi.fn(() => pending) };
    const state = createState(sourceApi);

    state.load({ page: 0 });
    sourceApi.list.mockReturnValue(of(sourcePage()));
    state.load({ page: 1 });

    expect(cancelled).toBe(true);
    expect(state.page()?.page).toBe(0);
  });
});

function createState(sourceApi: { list: ReturnType<typeof vi.fn> }): SourceListState {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [SourceListState, { provide: SourceApi, useValue: sourceApi }],
  });
  return TestBed.inject(SourceListState);
}

function sourcePage(): SourcePage {
  return {
    items: [
      {
        reference: 'source_ref',
        kind: 'PASTED_TEXT',
        status: 'READY',
        displayName: 'Reader notes',
        language: 'es',
        retryable: false,
        createdAt: '2026-07-26T20:00:00Z',
        updatedAt: '2026-07-26T20:01:00Z',
        version: 0,
      },
    ],
    page: 0,
    pageSize: 20,
    totalItems: 1,
    totalPages: 1,
  };
}
