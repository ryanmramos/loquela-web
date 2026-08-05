import { Routes } from '@angular/router';

import { anonymousGuard, authenticatedGuard, deletingGuard } from '@core/auth/session.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'app',
  },
  {
    path: 'login',
    title: 'Sign in',
    canActivate: [anonymousGuard],
    loadComponent: () => import('@app/features/auth/login-page').then((module) => module.LoginPage),
  },
  {
    path: 'app',
    canActivate: [authenticatedGuard],
    loadChildren: () =>
      import('./authenticated.routes').then((module) => module.authenticatedRoutes),
  },
  {
    path: 'account-deleting',
    title: 'Account deletion in progress',
    canActivate: [deletingGuard],
    loadComponent: () =>
      import('@app/features/auth/account-deleting-page').then(
        (module) => module.AccountDeletingPage,
      ),
  },
  {
    path: 'error',
    title: 'Something went wrong',
    loadComponent: () =>
      import('@core/errors/fatal-error-page').then((module) => module.FatalErrorPage),
  },
  {
    path: '**',
    redirectTo: 'app',
  },
];
