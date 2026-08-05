import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { provideRuntimeConfig } from '@core/config/runtime-config';

import {
  TELEMETRY_SINK,
  TelemetrySink,
  WebVitalEvent,
  WebVitalsTelemetry,
} from './web-vitals-telemetry';

describe('WebVitalsTelemetry', () => {
  const events: WebVitalEvent[] = [];
  const sink: TelemetrySink = { record: (event) => events.push(event) };

  beforeEach(() => {
    events.length = 0;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideRuntimeConfig({
          apiBasePath: '/api/v1',
          googleLoginPath: '/api/v1/auth/google',
          localLoginEnabled: false,
          supportUrl: '/support',
          environmentName: 'test',
          telemetryEnabled: true,
          releaseId: 'test-release',
        }),
        { provide: TELEMETRY_SINK, useValue: sink },
      ],
    });
  });

  it('coarsens values and emits only the reviewed fields', () => {
    TestBed.inject(WebVitalsTelemetry).capture('LCP', 1_234.56, '/app/sources/:sourceReference');

    expect(events).toHaveLength(1);
    expect(events[0]).toMatchObject({
      metric: 'LCP',
      value: 1230,
      routePattern: '/app/sources/:sourceReference',
      releaseId: 'test-release',
      environment: 'test',
      interfaceLanguage: 'en',
    });
    expect(events[0]?.navigationType).toEqual(expect.any(String));
    expect(Object.keys(events[0]).sort()).toEqual([
      'environment',
      'interfaceLanguage',
      'metric',
      'navigationType',
      'releaseId',
      'routePattern',
      'value',
    ]);
  });

  it('rejects full URLs, queries, identifiers, and invalid values', () => {
    const telemetry = TestBed.inject(WebVitalsTelemetry);
    telemetry.capture('CLS', 0.1, 'https://example.test/app');
    telemetry.capture('CLS', 0.1, '/app/lookup?query=private');
    telemetry.capture('CLS', 0.1, '/app/sources/529b7902-040a-4a40-8905-aa4ee06d2413');
    telemetry.capture('CLS', Number.NaN, '/app');

    expect(events).toEqual([]);
  });
});
