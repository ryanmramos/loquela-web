import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideApiConfiguration } from '@api/generated/api-configuration';

import { PracticeApi } from './practice-api';

describe('PracticeApi', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        PracticeApi,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiConfiguration(''),
      ],
    });
  });

  it('starts and resumes opaque server-owned sessions', () => {
    const api = TestBed.inject(PracticeApi);
    const http = TestBed.inject(HttpTestingController);

    api.active().subscribe();
    const active = http.expectOne('/api/v1/practice-sessions/active');
    expect(active.request.method).toBe('GET');
    active.flush(null, { status: 204, statusText: 'No Content' });

    api.start('opaque-deck').subscribe();
    const start = http.expectOne('/api/v1/practice-sessions');
    expect(start.request.method).toBe('POST');
    expect(start.request.body).toEqual({ deckReference: 'opaque-deck' });
    start.flush({ reference: 'opaque-session', status: 'ACTIVE' });

    api.get('opaque-session').subscribe();
    expect(http.expectOne('/api/v1/practice-sessions/opaque-session').request.method).toBe('GET');
    http.verify();
  });

  it('reveals and rates through generated JSON operations', () => {
    const api = TestBed.inject(PracticeApi);
    const http = TestBed.inject(HttpTestingController);

    api.reveal('opaque-session').subscribe();
    const reveal = http.expectOne('/api/v1/practice-sessions/opaque-session/reveal');
    expect(reveal.request.method).toBe('POST');
    reveal.flush({ answerRevealed: true });

    api.rate('opaque-session', 'request-id', 'GOT_IT').subscribe();
    const rate = http.expectOne('/api/v1/practice-sessions/opaque-session/ratings');
    expect(rate.request.method).toBe('POST');
    expect(rate.request.body).toEqual({ requestId: 'request-id', rating: 'GOT_IT' });
    rate.flush({ position: 2 });

    api.end('opaque-session').subscribe();
    const end = http.expectOne('/api/v1/practice-sessions/opaque-session');
    expect(end.request.method).toBe('DELETE');
    end.flush({ status: 'EXPIRED' });
    http.verify();
  });
});
