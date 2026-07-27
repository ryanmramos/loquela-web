import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { provideRuntimeConfig } from '@core/config/runtime-config';
import { apiProblemInterceptor } from '@core/http/api-problem.interceptor';

import { sessionExpirationInterceptor } from './session-expiration.interceptor';
import { SessionStore } from './session-store';

describe('sessionExpirationInterceptor', () => {
  it('clears authenticated memory and returns to login after an API 401', () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideRuntimeConfig({
          apiBasePath: '/api/v1',
          googleLoginPath: '/api/v1/auth/google',
          localLoginEnabled: true,
          supportUrl: '/support',
          environmentName: 'test',
          telemetryEnabled: false,
        }),
        provideHttpClient(withInterceptors([sessionExpirationInterceptor, apiProblemInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    const store = TestBed.inject(SessionStore);
    store.setAuthenticated({
      userId: '529b7902-040a-4a40-8905-aa4ee06d2413',
      displayName: 'Session User',
      email: 'session@example.com',
      sourceLanguage: 'es',
      definitionLanguage: 'en',
      accountState: 'ACTIVE',
      linkedAccounts: { google: true, whatsapp: false },
    });
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const http = TestBed.inject(HttpClient);
    const httpTesting = TestBed.inject(HttpTestingController);

    http.get('/api/v1/sources').subscribe({ error: () => undefined });
    httpTesting.expectOne('/api/v1/sources').flush(
      {
        type: 'https://loquela.app/problems/401',
        title: 'Authentication required',
        status: 401,
        code: 'AUTHENTICATION_REQUIRED',
        correlationId: 'expired-session',
        fieldErrors: [],
      },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(store.view()).toMatchObject({
      status: 'anonymous',
      notice: 'session-expired',
    });
    expect(navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { reason: 'session-expired', returnUrl: '/app' },
      replaceUrl: true,
    });
    httpTesting.verify();
  });
});
