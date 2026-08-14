import { Routes } from '@angular/router';
import { ConfirmationService } from 'primeng/api';

import {
  anonymousGuard,
  authenticatedGuard,
  deletingGuard,
  demoGuard,
} from '@core/auth/session.guard';

export const routes: Routes = [
  {
    path: 'demo',
    title: 'Try Loquela',
    canActivate: [demoGuard],
    providers: [ConfirmationService],
    loadComponent: () => import('@app/features/demo/demo-page').then((module) => module.DemoPage),
  },
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
