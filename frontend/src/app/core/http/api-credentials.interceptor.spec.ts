import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideRuntimeConfig, RuntimeConfig } from '@core/config/runtime-config';

import { apiCredentialsInterceptor } from './api-credentials.interceptor';

const runtimeConfig: RuntimeConfig = {
  apiBasePath: '/api/v1',
  googleLoginPath: '/oauth2/authorization/google',
  supportUrl: '/support',
  environmentName: 'test',
  telemetryEnabled: false,
};

describe('apiCredentialsInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRuntimeConfig(runtimeConfig),
        provideHttpClient(withInterceptors([apiCredentialsInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('sends session credentials to same-origin API requests', () => {
    http.get('/api/v1/session').subscribe();

    const request = httpTesting.expectOne('/api/v1/session');
    expect(request.request.withCredentials).toBe(true);
    request.flush({});
  });

  it('does not send session credentials to an arbitrary origin', () => {
    http.get('https://untrusted.example/api/v1/session').subscribe();

    const request = httpTesting.expectOne('https://untrusted.example/api/v1/session');
    expect(request.request.withCredentials).toBe(false);
    request.flush({});
  });
});
