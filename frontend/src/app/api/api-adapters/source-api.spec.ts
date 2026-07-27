import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideApiConfiguration } from '@api/generated/api-configuration';

import { SourceApi } from './source-api';

describe('SourceApi', () => {
  it('creates pasted text through the generated CSRF-protected mutation', () => {
    TestBed.configureTestingModule({
      providers: [
        SourceApi,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiConfiguration(''),
      ],
    });
    const api = TestBed.inject(SourceApi);
    const http = TestBed.inject(HttpTestingController);

    api.createPastedText('Una frase.').subscribe();
    const request = http.expectOne('/api/v1/sources');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ text: 'Una frase.' });
    request.flush({
      sourceId: 'source-id',
      reference: 'src_reference',
      status: 'RECEIVED',
      characterCount: 10,
    });
    http.verify();
  });
});
