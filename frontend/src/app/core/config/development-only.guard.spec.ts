import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { developmentOnlyGuard } from './development-only.guard';
import { RUNTIME_CONFIG, RuntimeConfig } from './runtime-config';

describe('developmentOnlyGuard', () => {
  it('allows the maintained foundation in local environments', () => {
    configure('local');
    const router = TestBed.inject(Router);

    const result = TestBed.runInInjectionContext(() =>
      developmentOnlyGuard({}, [], router.routerState.snapshot.root),
    );

    expect(result).toBe(true);
  });

  it('redirects production users away from the foundation', () => {
    configure('production');
    const router = TestBed.inject(Router);

    const result = TestBed.runInInjectionContext(() =>
      developmentOnlyGuard({}, [], router.routerState.snapshot.root),
    );

    expect(result).toEqual(router.parseUrl('/error'));
  });
});

function configure(environmentName: RuntimeConfig['environmentName']): void {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: RUNTIME_CONFIG,
        useValue: {
          apiBasePath: '/api/v1',
          googleLoginPath: '/oauth2/authorization/google',
          supportUrl: '/support',
          environmentName,
          telemetryEnabled: false,
        } satisfies RuntimeConfig,
      },
    ],
  });
}
