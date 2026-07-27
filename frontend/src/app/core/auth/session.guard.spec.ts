import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { firstValueFrom, Observable, of } from 'rxjs';

import { SessionFacade } from './session-facade';
import { authenticatedGuard } from './session.guard';
import { SessionView } from './session-store';

describe('authenticatedGuard', () => {
  it.each([
    ['authenticated', true],
    ['deleting', '/account-deleting'],
    ['anonymous', '/login?returnUrl=%2Fapp%2Fcards'],
    ['disabled', '/login?returnUrl=%2Fapp%2Fcards&reason=account-disabled'],
  ] as const)('routes a %s session correctly', async (status, expected) => {
    const view: SessionView = {
      status,
      ...(status === 'disabled' ? { notice: 'account-disabled' as const } : {}),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: SessionFacade,
          useValue: { ensureLoaded: () => of(view) },
        },
      ],
    });
    const router = TestBed.inject(Router);
    const result = await firstValueFrom(
      TestBed.runInInjectionContext(
        () =>
          authenticatedGuard(
            {} as ActivatedRouteSnapshot,
            { url: '/app/cards' } as RouterStateSnapshot,
          ) as Observable<boolean | UrlTree>,
      ),
    );

    expect(typeof result === 'boolean' ? result : router.serializeUrl(result)).toBe(expected);
  });
});
