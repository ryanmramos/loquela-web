import { Routes } from '@angular/router';

import { developmentOnlyGuard } from '@core/config/development-only.guard';
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
    title: 'Overview',
    canActivate: [authenticatedGuard],
    loadComponent: () =>
      import('@app/features/dashboard/dashboard-page').then((module) => module.DashboardPage),
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
