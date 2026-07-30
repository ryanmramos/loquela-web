import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { provideApiConfiguration } from '@api/generated/api-configuration';

import { LookupApi } from './lookup-api';

describe('LookupApi', () => {
  let api: LookupApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        LookupApi,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiConfiguration(''),
      ],
    });
    api = TestBed.inject(LookupApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the JSON acceptance reference used to start polling', async () => {
    const response = firstValueFrom(api.create('vivir', ['src_one'], ['YOUTUBE_VIDEO']));
    const request = http.expectOne('/api/v1/lookups');

    expect(request.request.method).toBe('POST');
    expect(request.request.responseType).toBe('json');
    expect(request.request.body).toEqual({
      term: 'vivir',
      sourceReferences: ['src_one'],
      sourceKinds: ['YOUTUBE_VIDEO'],
    });
    request.flush({
      reference: 'lookup_reference',
      expiresAt: '2026-07-30T00:00:00Z',
    });

    await expect(response).resolves.toEqual({
      reference: 'lookup_reference',
      expiresAt: '2026-07-30T00:00:00Z',
    });
  });

  it('rejects a malformed acceptance instead of leaving the UI pending forever', async () => {
    const response = firstValueFrom(api.create('vivir', [], []));
    http.expectOne('/api/v1/lookups').flush({});

    await expect(response).rejects.toThrow('missing its reference');
  });
});
