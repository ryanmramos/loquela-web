import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Welcome',
    pathMatch: 'full',
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
