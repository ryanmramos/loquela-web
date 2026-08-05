import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { provideApiConfiguration } from '@api/generated/api-configuration';

import { ActivityApi } from './activity-api';

describe('ActivityApi', () => {
  let api: ActivityApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideApiConfiguration(''), provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ActivityApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends filters and the opaque cursor to the chronological feed', async () => {
    const result = firstValueFrom(
      api.list({ category: 'LOOKUP', lifecycle: 'ACTIVE', cursor: 'opaque_cursor', limit: 20 }),
    );
    const request = http.expectOne(
      '/api/v1/activity?category=LOOKUP&lifecycle=ACTIVE&cursor=opaque_cursor&limit=20',
    );
    expect(request.request.method).toBe('GET');
    request.flush({ items: [], activeCount: 2 });
    await expect(result).resolves.toEqual({ items: [], activeCount: 2 });
  });

  it('uses POST for a bounded status query', async () => {
    const result = firstValueFrom(api.statuses(['act_src_one']));
    const request = http.expectOne('/api/v1/activity/status-query');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ references: ['act_src_one'] });
    request.flush({ items: [], activeCount: 0 });
    await expect(result).resolves.toEqual({ items: [], activeCount: 0 });
  });
});
