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
  googleLoginPath: '/oauth2/authorization/google',
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
});
