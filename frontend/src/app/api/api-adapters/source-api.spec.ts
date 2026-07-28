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

  it('passes pagination and filters to the generated source list operation', () => {
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

    api.list({ page: 2, pageSize: 20, search: 'reader', kind: 'PDF', status: 'READY' }).subscribe();
    const request = http.expectOne(
      '/api/v1/sources?page=2&pageSize=20&search=reader&kind=PDF&status=READY',
    );
    expect(request.request.method).toBe('GET');
    request.flush({ items: [], page: 2, pageSize: 20, totalItems: 0, totalPages: 0 });
    http.verify();
  });

  it('uses opaque references for detail, rename, retry, and delete calls', () => {
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

    api.get('opaque_ref').subscribe();
    const get = http.expectOne('/api/v1/sources/opaque_ref');
    expect(get.request.method).toBe('GET');
    get.flush({});

    api.rename('opaque_ref', 'New name', 3).subscribe();
    const rename = http.expectOne('/api/v1/sources/opaque_ref');
    expect(rename.request.method).toBe('PATCH');
    expect(rename.request.body).toEqual({ displayName: 'New name', version: 3 });
    rename.flush({});

    api.retry('opaque_ref').subscribe();
    const retry = http.expectOne('/api/v1/sources/opaque_ref/retry');
    expect(retry.request.method).toBe('POST');
    retry.flush({});

    api.delete('opaque_ref').subscribe();
    const deletion = http.expectOne('/api/v1/sources/opaque_ref');
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush({});
    http.verify();
  });
});
