import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { safeAuthenticatedReturnUrl } from './return-url';
import { SessionFacade } from './session-facade';

export const authenticatedGuard: CanActivateFn = (_route, state) => {
  const facade = inject(SessionFacade);
  const router = inject(Router);

  return facade.ensureLoaded().pipe(
    map((session) => {
      if (session.status === 'authenticated') {
        return true;
      }
      if (session.status === 'deleting') {
        return router.parseUrl('/account-deleting');
      }
      const returnUrl = safeAuthenticatedReturnUrl(state.url);
      const reason = session.notice === 'account-disabled' ? 'account-disabled' : undefined;
      return router.createUrlTree(['/login'], {
        queryParams: {
          returnUrl,
          ...(reason ? { reason } : {}),
        },
      });
    }),
  );
};

export const anonymousGuard: CanActivateFn = () => {
  const facade = inject(SessionFacade);
  const router = inject(Router);

  return facade.ensureLoaded().pipe(
    map((session) => {
      if (session.status === 'authenticated') {
        return router.parseUrl('/app');
      }
      if (session.status === 'deleting') {
        return router.parseUrl('/account-deleting');
      }
      return true;
    }),
  );
};

export const deletingGuard: CanActivateFn = () => {
  const facade = inject(SessionFacade);
  const router = inject(Router);

  return facade
    .ensureLoaded()
    .pipe(
      map((session) =>
        session.status === 'deleting'
          ? true
          : session.status === 'authenticated'
            ? router.parseUrl('/app')
            : router.parseUrl('/login'),
      ),
    );
};
