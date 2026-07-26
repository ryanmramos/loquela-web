import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { RUNTIME_CONFIG } from '@core/config/runtime-config';

import { isApiRequest } from './api-request';

export const apiCredentialsInterceptor: HttpInterceptorFn = (request, next) => {
  const config = inject(RUNTIME_CONFIG);

  if (!isApiRequest(request, config)) {
    return next(request);
  }

  return next(request.clone({ withCredentials: true }));
};
