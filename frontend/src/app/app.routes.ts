import { Routes } from '@angular/router';

import { developmentOnlyGuard } from '@core/config/development-only.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'foundation',
  },
  {
    path: 'foundation',
    title: 'Design foundation',
    canMatch: [developmentOnlyGuard],
    loadComponent: () =>
      import('@core/layout/foundation-page').then((module) => module.FoundationPage),
  },
  {
    path: 'error',
    title: 'Something went wrong',
    loadComponent: () =>
      import('@core/errors/fatal-error-page').then((module) => module.FatalErrorPage),
  },
];
