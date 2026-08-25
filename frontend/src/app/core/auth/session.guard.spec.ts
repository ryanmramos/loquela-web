import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { firstValueFrom, Observable, of } from 'rxjs';

import { BrowserSession } from '@api/api-adapters/session-api';

import { SessionFacade } from './session-facade';
import { authenticatedGuard } from './session.guard';
import { SessionView } from './session-store';

describe('authenticatedGuard', () => {
  const user: BrowserSession = {
    userId: 'test-user',
    displayName: null,
    email: null,
    sourceLanguage: 'es',
    definitionLanguage: 'en',
    accountState: 'ACTIVE',
    roles: ['USER'],
    linkedAccounts: { google: false, whatsapp: false },
  };

  it.each([
    ['authenticated', true],
    ['deleting', '/account-deleting'],
    ['anonymous', '/login?returnUrl=%2Fapp%2Fcards'],
    ['disabled', '/login?returnUrl=%2Fapp%2Fcards&reason=account-disabled'],
  ] as const)('routes a %s session correctly', async (status, expected) => {
    const view: SessionView =
      status === 'authenticated'
        ? { status, user }
        : status === 'deleting'
          ? { status, user: { ...user, accountState: 'DELETING' } }
          : status === 'disabled'
            ? { status, notice: 'account-disabled' }
            : { status };
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
