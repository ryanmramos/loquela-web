import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { provideRuntimeConfig, RuntimeConfig } from '@core/config/runtime-config';
import { apiCredentialsInterceptor } from '@core/http/api-credentials.interceptor';
import { provideApiConfiguration } from '@api/generated/api-configuration';

import { SessionApi } from './session-api';

const runtimeConfig: RuntimeConfig = {
  apiBasePath: '/api/v1',
  googleLoginPath: '/api/v1/auth/google',
  localLoginEnabled: true,
  supportUrl: '/support',
  environmentName: 'test',
  telemetryEnabled: false,
};

describe('SessionApi', () => {
  let api: SessionApi;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRuntimeConfig(runtimeConfig),
        provideApiConfiguration(''),
        provideHttpClient(withInterceptors([apiCredentialsInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    api = TestBed.inject(SessionApi);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('calls the generated same-origin session operation and maps its response', async () => {
    const response = firstValueFrom(api.getCurrent());
    const request = httpTesting.expectOne('/api/v1/session');

    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBe(true);
    request.flush({
      userId: '529b7902-040a-4a40-8905-aa4ee06d2413',
      displayName: 'Session User',
      email: 'session@example.com',
      sourceLanguage: 'es',
      definitionLanguage: 'en',
      interfaceLanguage: null,
      accountState: 'ACTIVE',
      roles: ['USER'],
      linkedAccounts: {
        google: true,
        whatsapp: false,
      },
    });

    await expect(response).resolves.toEqual({
      userId: '529b7902-040a-4a40-8905-aa4ee06d2413',
      displayName: 'Session User',
      email: 'session@example.com',
      sourceLanguage: 'es',
      definitionLanguage: 'en',
      interfaceLanguage: null,
      accountState: 'ACTIVE',
      roles: ['USER'],
      linkedAccounts: {
        google: true,
        whatsapp: false,
      },
    });
  });

  it('preserves nullable Google profile metadata', async () => {
    const response = firstValueFrom(api.getCurrent());
    httpTesting.expectOne('/api/v1/session').flush({
      userId: '529b7902-040a-4a40-8905-aa4ee06d2413',
      sourceLanguage: 'es',
      definitionLanguage: 'en',
      accountState: 'ACTIVE',
      roles: ['USER'],
      linkedAccounts: {
        google: true,
        whatsapp: false,
      },
    });

    await expect(response).resolves.toMatchObject({
      displayName: null,
      email: null,
    });
  });

  it('uses the local-only login boundary through the same API security pipeline', async () => {
    const response = firstValueFrom(api.localLogin('reviewer'));
    const request = httpTesting.expectOne('/api/v1/auth/local-login');

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ accountId: 'reviewer' });
    expect(request.request.withCredentials).toBe(true);
    request.flush({
      userId: '3fe1c40d-3505-4e29-98da-a97420d71166',
      displayName: 'Local Reviewer',
      email: 'reviewer@local.loquela.test',
      sourceLanguage: 'es',
      definitionLanguage: 'en',
      accountState: 'ACTIVE',
      roles: ['USER'],
      linkedAccounts: { google: true, whatsapp: false },
    });

    await expect(response).resolves.toMatchObject({
      displayName: 'Local Reviewer',
      accountState: 'ACTIVE',
    });
  });

  it('logs out through the generated CSRF-protected operation', async () => {
    const response = firstValueFrom(api.logout());
    const request = httpTesting.expectOne('/api/v1/session/logout');

    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    request.flush(null, { status: 204, statusText: 'No Content' });

    await expect(response).resolves.toBeUndefined();
  });
});
