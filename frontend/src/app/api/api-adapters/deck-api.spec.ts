import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideApiConfiguration } from '@api/generated/api-configuration';

import { DeckApi } from './deck-api';

describe('DeckApi', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        DeckApi,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiConfiguration(''),
      ],
    });
  });

  it('uses generated operations for deck lifecycle mutations', () => {
    const api = TestBed.inject(DeckApi);
    const http = TestBed.inject(HttpTestingController);

    api.create({ name: 'Travel', themeColor: 'ORANGE' }).subscribe();
    const create = http.expectOne('/api/v1/decks');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ name: 'Travel', themeColor: 'ORANGE' });
    create.flush({});

    api.update('opaque_deck', { name: 'Trips', themeColor: 'BLUE' }).subscribe();
    const update = http.expectOne('/api/v1/decks/opaque_deck');
    expect(update.request.method).toBe('PATCH');
    update.flush({});

    api.delete('opaque_deck').subscribe();
    const deletion = http.expectOne('/api/v1/decks/opaque_deck');
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null);
    http.verify();
  });

  it('bounds and forwards deck list pagination and search', () => {
    const api = TestBed.inject(DeckApi);
    const http = TestBed.inject(HttpTestingController);

    api.list(2, 50, 'Travel').subscribe();
    const list = http.expectOne('/api/v1/decks?search=Travel&page=2&pageSize=50');
    expect(list.request.method).toBe('GET');
    list.flush({ items: [], page: 2, pageSize: 50, totalItems: 0, totalPages: 0 });
    http.verify();
  });

  it('queues and resumes export state through opaque references', () => {
    const api = TestBed.inject(DeckApi);
    const http = TestBed.inject(HttpTestingController);

    api.export('opaque_deck').subscribe();
    const create = http.expectOne('/api/v1/decks/opaque_deck/exports');
    expect(create.request.method).toBe('POST');
    create.flush({ state: 'QUEUED' });

    api.exports().subscribe();
    const history = http.expectOne('/api/v1/exports?page=0&pageSize=20');
    expect(history.request.method).toBe('GET');
    history.flush({ items: [] });

    api.exportStatus('opaque_export').subscribe();
    const status = http.expectOne('/api/v1/exports/opaque_export');
    expect(status.request.method).toBe('GET');
    status.flush({ state: 'READY' });

    expect(api.downloadUrl('opaque/export')).toBe('/api/v1/exports/opaque%2Fexport/download');
    http.verify();
  });
});
