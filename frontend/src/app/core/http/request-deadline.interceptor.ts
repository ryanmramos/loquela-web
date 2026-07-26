import { HttpContextToken, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError, TimeoutError, timeout } from 'rxjs';

import { RUNTIME_CONFIG } from '@core/config/runtime-config';
import { ApiProblemError } from '@core/errors/api-problem';

import { isApiRequest } from './api-request';
import { CORRELATION_ID_HEADER } from './correlation-id.interceptor';

const DEFAULT_REQUEST_DEADLINE_MS = 15_000;

/**
 * Use zero for explicitly long-lived requests such as an upload. Interactive API calls remain
 * bounded by default so a broken connection cannot leave the interface waiting indefinitely.
 */
export const REQUEST_DEADLINE_MS = new HttpContextToken<number>(() => DEFAULT_REQUEST_DEADLINE_MS);

export const requestDeadlineInterceptor: HttpInterceptorFn = (request, next) => {
  const config = inject(RUNTIME_CONFIG);
  const deadline = request.context.get(REQUEST_DEADLINE_MS);

  if (!isApiRequest(request, config) || deadline === 0) {
    return next(request);
  }

  return next(request).pipe(
    timeout({ each: deadline }),
    catchError((error: unknown) => {
      if (!(error instanceof TimeoutError)) {
        return throwError(() => error);
      }

      return throwError(
        () =>
          new ApiProblemError({
            type: 'about:blank',
            title: 'Request timed out',
            status: 0,
            code: 'CLIENT_TIMEOUT',
            detail: 'The request took too long. Try again.',
            correlationId: request.headers.get(CORRELATION_ID_HEADER) ?? 'unavailable',
          }),
      );
    }),
  );
};
