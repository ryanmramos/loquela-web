import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { provideApiConfiguration } from '@api/generated/api-configuration';

import { CardApi } from './card-api';

describe('CardApi', () => {
  let api: CardApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        CardApi,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiConfiguration(''),
      ],
    });
    api = TestBed.inject(CardApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('passes search and deck filters through the generated list operation', async () => {
    const response = firstValueFrom(api.list('vivir', 'deck_reference'));
    const request = http.expectOne(
      '/api/v1/cards?page=0&pageSize=50&search=vivir&deckReference=deck_reference',
    );

    expect(request.request.method).toBe('GET');
    request.flush({ items: [], page: 0, pageSize: 50, totalItems: 0, totalPages: 0 });

    await expect(response).resolves.toMatchObject({ items: [], totalItems: 0 });
  });

  it('uses the card version supplied by the form for optimistic updates', async () => {
    const body = {
      deckReference: 'deck_reference_value',
      term: 'vivir',
      definition: 'to live',
      version: 4,
    };
    const response = firstValueFrom(api.update('card_reference', body));
    const request = http.expectOne('/api/v1/cards/card_reference');

    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual(body);
    request.flush({
      reference: 'card_reference',
      deckReference: 'deck_reference_value',
      deckName: 'Vocabulary',
      noteType: 'BASIC',
      term: 'vivir',
      definition: 'to live',
      version: 5,
      updatedAt: '2026-07-30T00:00:00Z',
    });

    await expect(response).resolves.toMatchObject({ reference: 'card_reference', version: 5 });
  });
});
