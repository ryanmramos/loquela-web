import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';

import { RUNTIME_CONFIG } from './runtime-config';

export const developmentOnlyGuard: CanMatchFn = () => {
  const config = inject(RUNTIME_CONFIG);
  const router = inject(Router);

  return config.environmentName === 'local' || config.environmentName === 'test'
    ? true
    : router.parseUrl('/error');
};
