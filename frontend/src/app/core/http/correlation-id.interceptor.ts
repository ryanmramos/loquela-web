import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { RUNTIME_CONFIG } from '@core/config/runtime-config';

import { isApiRequest } from './api-request';

export const CORRELATION_ID_HEADER = 'X-Correlation-ID';

export const correlationIdInterceptor: HttpInterceptorFn = (request, next) => {
  const config = inject(RUNTIME_CONFIG);

  if (!isApiRequest(request, config) || request.headers.has(CORRELATION_ID_HEADER)) {
    return next(request);
  }

  return next(
    request.clone({
      setHeaders: { [CORRELATION_ID_HEADER]: createCorrelationId() },
    }),
  );
};

function createCorrelationId(): string {
  return globalThis.crypto.randomUUID();
}
