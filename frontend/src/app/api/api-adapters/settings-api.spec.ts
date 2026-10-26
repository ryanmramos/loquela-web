import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { provideApiConfiguration } from '@api/generated/api-configuration';
import { provideRuntimeConfig, RuntimeConfig } from '@core/config/runtime-config';
import { apiCredentialsInterceptor } from '@core/http/api-credentials.interceptor';

import { SettingsApi } from './settings-api';

const runtimeConfig: RuntimeConfig = {
  apiBasePath: '/api/v1',
  googleLoginPath: '/api/v1/auth/google',
  localLoginEnabled: true,
  supportUrl: '/support',
  environmentName: 'test',
  telemetryEnabled: false,
};

describe('SettingsApi', () => {
  let api: SettingsApi;
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
    api = TestBed.inject(SettingsApi);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads the current pair and backend-supported pairs', async () => {
    const response = firstValueFrom(api.getLanguages());
    const request = httpTesting.expectOne('/api/v1/settings/languages');

    expect(request.request.method).toBe('GET');
    request.flush({
      languagePair: { sourceLanguage: 'es', definitionLanguage: 'en' },
      supportedPairs: [
        { sourceLanguage: 'es', definitionLanguage: 'en' },
        { sourceLanguage: 'en', definitionLanguage: 'es' },
      ],
    });

    await expect(response).resolves.toEqual({
      languagePair: { sourceLanguage: 'es', definitionLanguage: 'en' },
      supportedPairs: [
        { sourceLanguage: 'es', definitionLanguage: 'en' },
        { sourceLanguage: 'en', definitionLanguage: 'es' },
      ],
    });
  });

  it('updates the complete language pair', async () => {
    const response = firstValueFrom(
      api.updateLanguages({ sourceLanguage: 'en', definitionLanguage: 'es' }),
    );
    const request = httpTesting.expectOne('/api/v1/settings/languages');

    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({
      sourceLanguage: 'en',
      definitionLanguage: 'es',
    });
    request.flush({
      languagePair: { sourceLanguage: 'en', definitionLanguage: 'es' },
      supportedPairs: [{ sourceLanguage: 'en', definitionLanguage: 'es' }],
    });

    await expect(response).resolves.toMatchObject({
      languagePair: { sourceLanguage: 'en', definitionLanguage: 'es' },
    });
  });

  it('loads and updates independent settings preferences', async () => {
    const loaded = firstValueFrom(api.get());
    const getRequest = httpTesting.expectOne('/api/v1/settings');
    expect(getRequest.request.method).toBe('GET');
    getRequest.flush({
      profile: { displayName: 'Test User', email: 'test@example.com' },
      linkedAccounts: { google: true, googleLinkedAt: '2026-07-18T00:00:00Z', whatsapp: false },
      languagePair: { sourceLanguage: 'es', definitionLanguage: 'en' },
      supportedPairs: [{ sourceLanguage: 'es', definitionLanguage: 'en' }],
      automaticCaptionFallback: true,
      exportDeckPrefix: 'Loquela — ',
      exportIncludeProvenance: true,
    });
    await expect(loaded).resolves.toMatchObject({
      profile: { displayName: 'Test User' },
      automaticCaptionFallback: true,
    });

    const updated = firstValueFrom(
      api.update({
        sourceLanguage: 'es',
        definitionLanguage: 'en',
        automaticCaptionFallback: false,
        exportDeckPrefix: 'Mine — ',
        exportIncludeProvenance: false,
      }),
    );
    const patchRequest = httpTesting.expectOne('/api/v1/settings');
    expect(patchRequest.request.method).toBe('PATCH');
    patchRequest.flush({
      languagePair: { sourceLanguage: 'es', definitionLanguage: 'en' },
      supportedPairs: [{ sourceLanguage: 'es', definitionLanguage: 'en' }],
      automaticCaptionFallback: false,
      exportDeckPrefix: 'Mine — ',
      exportIncludeProvenance: false,
    });
    await expect(updated).resolves.toMatchObject({ automaticCaptionFallback: false });
  });

  it('submits the account-deletion phrase without a client-selected user', async () => {
    const response = firstValueFrom(api.deleteAccount('DELETE MY ACCOUNT'));
    const request = httpTesting.expectOne('/api/v1/account-deletion-requests');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ confirmationPhrase: 'DELETE MY ACCOUNT' });
    request.flush({ status: 'DELETING' });
    await expect(response).resolves.toBeUndefined();
  });

  it('loads the safe account-deletion projection', async () => {
    const response = firstValueFrom(api.currentDeletion());
    const request = httpTesting.expectOne('/api/v1/account-deletion-requests/current');
    expect(request.request.method).toBe('GET');
    request.flush({
      status: 'NEEDS_ATTENTION',
      updatedAt: '2026-08-04T20:00:00Z',
      supportCode: 'opaque_support_code',
    });
    await expect(response).resolves.toEqual({
      status: 'NEEDS_ATTENTION',
      updatedAt: '2026-08-04T20:00:00Z',
      supportCode: 'opaque_support_code',
    });
  });
});
