import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { SourceApi, SourceDetails } from '@api/api-adapters/source-api';

import { SourceDetailsState } from './source-details-state';

describe('SourceDetailsState', () => {
  it('loads a terminal source from its durable URL', () => {
    vi.useFakeTimers();
    const sourceApi = api({ ...details(), status: 'READY' });
    const state = createState(sourceApi);

    state.load('source_ref');
    vi.advanceTimersByTime(0);

    expect(sourceApi.get).toHaveBeenCalledWith('source_ref');
    expect(state.source()?.status).toBe('READY');
    expect(state.loadStatus()).toBe('success');
    vi.useRealTimers();
  });

  it('renames with the current optimistic-lock version then refreshes', () => {
    vi.useFakeTimers();
    const sourceApi = api(details());
    const state = createState(sourceApi);
    state.load('source_ref');
    vi.advanceTimersByTime(0);

    state.rename('A better name');
    vi.advanceTimersByTime(0);

    expect(sourceApi.rename).toHaveBeenCalledWith('source_ref', 'A better name', 4);
    expect(sourceApi.get).toHaveBeenCalledTimes(2);
    vi.useRealTimers();
  });
});

function createState(sourceApi: ReturnType<typeof api>): SourceDetailsState {
  Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
  Object.defineProperty(navigator, 'onLine', { value: true, configurable: true });
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [SourceDetailsState, { provide: SourceApi, useValue: sourceApi }],
  });
  return TestBed.inject(SourceDetailsState);
}

function api(source: SourceDetails) {
  return {
    get: vi.fn(() => of(source)),
    rename: vi.fn(() => of(source)),
    retry: vi.fn(() => of({ reference: source.reference, status: 'RECEIVED' })),
    delete: vi.fn(() => of({ reference: source.reference, status: 'DELETING' })),
  };
}

function details(): SourceDetails {
  return {
    reference: 'source_ref',
    kind: 'PDF',
    status: 'FAILED',
    displayName: 'Reader',
    language: 'es',
    origin: 'WEBSITE_UPLOAD',
    errorCode: 'EXTRACTION_TIMEOUT',
    retryable: true,
    createdAt: '2026-07-26T20:00:00Z',
    updatedAt: '2026-07-26T20:01:00Z',
    version: 4,
  };
}
