import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideRuntimeConfig, RuntimeConfig } from '@core/config/runtime-config';
import { ApiProblemError } from '@core/errors/api-problem';

import { apiProblemInterceptor } from './api-problem.interceptor';

const runtimeConfig: RuntimeConfig = {
  apiBasePath: '/api/v1',
  googleLoginPath: '/api/v1/auth/google',
  localLoginEnabled: true,
  supportUrl: '/support',
  environmentName: 'test',
  telemetryEnabled: false,
};

describe('apiProblemInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRuntimeConfig(runtimeConfig),
        provideHttpClient(withInterceptors([apiProblemInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('maps a backend problem response to ApiProblemError', () => {
    const expectedProblem = {
      type: 'https://loquela.example/problems/source-not-found',
      title: 'Source not found',
      status: 404,
      code: 'SOURCE_NOT_FOUND',
      detail: 'The source no longer exists.',
      correlationId: 'server-correlation-id',
      fieldErrors: [],
    };
    let receivedError: unknown;

    http.get('/api/v1/sources/missing').subscribe({
      error: (error: unknown) => {
        receivedError = error;
      },
    });

    httpTesting.expectOne('/api/v1/sources/missing').flush(expectedProblem, {
      status: 404,
      statusText: 'Not Found',
    });

    expect(receivedError).toBeInstanceOf(ApiProblemError);
    expect((receivedError as ApiProblemError).problem).toEqual(expectedProblem);
  });

  it('does not expose an unknown response body', () => {
    let receivedError: unknown;

    http.get('/api/v1/session').subscribe({
      error: (error: unknown) => {
        receivedError = error;
      },
    });

    httpTesting.expectOne('/api/v1/session').flush('database host is secret.internal', {
      headers: { 'X-Correlation-ID': 'safe-support-id' },
      status: 500,
      statusText: 'Server Error',
    });

    expect((receivedError as ApiProblemError).problem).toEqual({
      type: 'about:blank',
      title: 'Request failed',
      status: 500,
      code: 'UNEXPECTED_API_ERROR',
      detail: 'The request could not be completed. Try again.',
      correlationId: 'safe-support-id',
    });
  });

  it('maps a problem response when a download requests a blob', async () => {
    const expectedProblem = {
      type: 'https://loquela.example/problems/demo-busy',
      title: 'Demo busy',
      status: 429,
      code: 'DEMO_BUSY',
      correlationId: 'server-correlation-id',
      fieldErrors: [],
    };
    const receivedError = await new Promise<unknown>((resolve) => {
      http.post('/api/v1/demo/exports/download', {}, { responseType: 'blob' }).subscribe({
        error: resolve,
      });
      httpTesting
        .expectOne('/api/v1/demo/exports/download')
        .flush(new Blob([JSON.stringify(expectedProblem)], { type: 'application/problem+json' }), {
          status: 429,
          statusText: 'Too Many Requests',
        });
    });

    expect(receivedError).toBeInstanceOf(ApiProblemError);
    expect((receivedError as ApiProblemError).problem).toEqual(expectedProblem);
  });
});
