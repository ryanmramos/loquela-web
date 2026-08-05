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
    canActivate: [authenticatedGuard],
    loadComponent: () =>
      import('@app/features/shell/authenticated-shell-page').then(
        (module) => module.AuthenticatedShellPage,
      ),
    children: [
      {
        path: '',
        title: 'Overview',
        loadComponent: () =>
          import('@app/features/dashboard/dashboard-page').then((module) => module.DashboardPage),
      },
      {
        path: 'sources',
        title: 'Sources',
        loadComponent: () =>
          import('@app/features/sources/source-list/source-list').then(
            (module) => module.SourceList,
          ),
      },
      {
        path: 'sources/new',
        title: 'Add source',
        loadComponent: () =>
          import('@app/features/sources/source-intake/source-intake').then(
            (module) => module.SourceIntake,
          ),
      },
      {
        path: 'sources/:sourceReference',
        title: 'Source details',
        loadComponent: () =>
          import('@app/features/sources/source-details/source-details').then(
            (module) => module.SourceDetails,
          ),
      },
      {
        path: 'cards',
        title: 'Cards',
        loadComponent: () =>
          import('@app/features/cards/cards-page').then((module) => module.CardsPage),
      },
      {
        path: 'decks',
        title: 'Decks',
        loadComponent: () =>
          import('@app/features/decks/decks-page').then((module) => module.DecksPage),
      },
      {
        path: 'study',
        title: 'Practice',
        loadComponent: () =>
          import('@app/features/practice/practice-page').then((module) => module.PracticePage),
      },
      {
        path: 'activity',
        title: 'Activity',
        loadComponent: () =>
          import('@app/features/activity/activity-page').then((module) => module.ActivityPage),
      },
      {
        path: 'lookup',
        title: 'Lookup',
        loadComponent: () =>
          import('@app/features/lookup/lookup-page').then((module) => module.LookupPage),
      },
      {
        path: 'settings',
        title: 'Settings',
        loadComponent: () =>
          import('@app/features/settings/settings-page').then((module) => module.SettingsPage),
      },
    ],
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
