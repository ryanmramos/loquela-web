import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideApiConfiguration } from '@api/generated/api-configuration';

import { DashboardApi } from './dashboard-api';

describe('DashboardApi', () => {
  it('loads every dashboard section through one aggregate request', () => {
    TestBed.configureTestingModule({
      providers: [
        DashboardApi,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiConfiguration(''),
      ],
    });
    const api = TestBed.inject(DashboardApi);
    const http = TestBed.inject(HttpTestingController);
    let result: unknown;

    api.get().subscribe((snapshot) => (result = snapshot));
    const request = http.expectOne('/api/v1/dashboard');
    expect(request.request.method).toBe('GET');
    request.flush(dashboardResponse());

    expect(result).toEqual(dashboardResponse());
    http.verify();
  });
});

function dashboardResponse() {
  return {
    generatedAt: '2026-07-26T20:00:00Z',
    languagePair: { sourceLanguage: 'es', definitionLanguage: 'en' },
    cards: {
      status: 'AVAILABLE' as const,
      cardsToStudy: 12,
      totalCards: 384,
      newCardsThisWeek: 16,
    },
    sources: {
      status: 'AVAILABLE' as const,
      totalSources: 26,
      readySources: 24,
      processingSources: 2,
    },
    jobs: { status: 'AVAILABLE' as const, activeJobs: 2 },
    activity: { status: 'AVAILABLE' as const, items: [] },
  };
}
