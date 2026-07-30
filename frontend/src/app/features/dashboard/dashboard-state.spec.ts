import { TestBed } from '@angular/core/testing';
import { Observable, of, Subject } from 'rxjs';

import { DashboardApi, DashboardSnapshot } from '@api/api-adapters/dashboard-api';
import { SourceApi } from '@api/api-adapters/source-api';
import { ShellDashboardStore } from '@core/layout/shell-dashboard-store';

import { DashboardState } from './dashboard-state';

describe('DashboardState', () => {
  it('loads zero and large datasets without changing the view-state shape', () => {
    const dashboardApi = { get: vi.fn(() => of(snapshot(0))) };
    const state = createState(dashboardApi);

    state.load();
    expect(state.snapshot()?.cards.totalCards).toBe(0);

    dashboardApi.get.mockReturnValue(of(snapshot(9_876_543)));
    state.load();
    expect(state.snapshot()?.cards.totalCards).toBe(9_876_543);
    expect(state.loadStatus()).toBe('success');
  });

  it('retains partial data and marks an old response stale', () => {
    const partial: DashboardSnapshot = {
      ...snapshot(12, '2026-01-01T00:00:00Z'),
      cards: {
        status: 'UNAVAILABLE',
        cardsToStudy: null,
        totalCards: null,
        newCardsThisWeek: null,
      },
    };
    const state = createState({ get: vi.fn(() => of(partial)) });

    state.load();

    expect(state.loadStatus()).toBe('success');
    expect(state.snapshot()?.sources.totalSources).toBe(4);
    expect(state.stale()).toBe(true);
  });

  it('cancels an in-flight dashboard request when the feature is destroyed', () => {
    let cancelled = false;
    const pending = new Observable<DashboardSnapshot>(() => () => {
      cancelled = true;
    });
    const state = createState({ get: vi.fn(() => pending) });

    state.load();
    expect(state.loadStatus()).toBe('loading');
    TestBed.resetTestingModule();
    expect(cancelled).toBe(true);
  });
});

function createState(dashboardApi: { get: () => Observable<DashboardSnapshot> }): DashboardState {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      DashboardState,
      ShellDashboardStore,
      { provide: DashboardApi, useValue: dashboardApi },
      { provide: SourceApi, useValue: { createPastedText: vi.fn(() => new Subject()) } },
    ],
  });
  return TestBed.inject(DashboardState);
}

function snapshot(totalCards: number, generatedAt = new Date().toISOString()): DashboardSnapshot {
  return {
    generatedAt,
    languagePair: { sourceLanguage: 'es', definitionLanguage: 'en' },
    cards: {
      status: 'AVAILABLE',
      cardsToStudy: totalCards,
      totalCards,
      newCardsThisWeek: totalCards,
    },
    sources: {
      status: 'AVAILABLE',
      totalSources: 4,
      readySources: 3,
      processingSources: 1,
    },
    storage: { status: 'AVAILABLE', usedBytes: 1_572_864 },
    jobs: { status: 'AVAILABLE', activeJobs: 1 },
    activity: { status: 'AVAILABLE', items: [] },
  };
}
