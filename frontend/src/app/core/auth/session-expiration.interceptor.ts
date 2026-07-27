import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { RUNTIME_CONFIG } from '@core/config/runtime-config';
import { ApiProblemError } from '@core/errors/api-problem';
import { isApiRequest } from '@core/http/api-request';

import { safeAuthenticatedReturnUrl } from './return-url';
import { SessionStore } from './session-store';

@Injectable({ providedIn: 'root' })
export class SessionExpirationHandler {
  private readonly router = inject(Router);
  private readonly store = inject(SessionStore);

  handleUnauthorized(): void {
    const status = this.store.view().status;
    if (status !== 'authenticated' && status !== 'deleting') {
      return;
    }

    const returnUrl = safeAuthenticatedReturnUrl(this.router.url);
    this.store.setAnonymous('session-expired');
    void this.router.navigate(['/login'], {
      queryParams: { reason: 'session-expired', returnUrl },
      replaceUrl: true,
    });
  }
}

export const sessionExpirationInterceptor: HttpInterceptorFn = (request, next) => {
  const config = inject(RUNTIME_CONFIG);
  const handler = inject(SessionExpirationHandler);

  if (!isApiRequest(request, config)) {
    return next(request);
  }

  return next(request).pipe(
    catchError((error: unknown) => {
      const status =
        error instanceof ApiProblemError
          ? error.problem.status
          : error instanceof HttpErrorResponse
            ? error.status
            : undefined;
      if (status === 401) {
        handler.handleUnauthorized();
      }
      return throwError(() => error);
    }),
  );
};
