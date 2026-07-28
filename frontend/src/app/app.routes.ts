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
        data: {
          featureKey: 'cards',
          eyebrow: 'Card library',
          heading: 'Cards',
          description:
            'Card management arrives in Step 11. Dashboard filters are preserved in this route.',
        },
        loadComponent: () =>
          import('@app/features/placeholder/feature-placeholder-page').then(
            (module) => module.FeaturePlaceholderPage,
          ),
      },
      {
        path: 'decks',
        title: 'Decks',
        data: {
          featureKey: 'decks',
          eyebrow: 'Organization',
          heading: 'Decks',
          description: 'Deck management and exports arrive in Step 12.',
        },
        loadComponent: () =>
          import('@app/features/placeholder/feature-placeholder-page').then(
            (module) => module.FeaturePlaceholderPage,
          ),
      },
      {
        path: 'study',
        title: 'Practice',
        data: {
          featureKey: 'study',
          eyebrow: 'Loquela practice',
          heading: 'Practice',
          description:
            'The Loquela-owned practice workflow arrives in Step 13. No Anki scheduling state is inferred here.',
        },
        loadComponent: () =>
          import('@app/features/placeholder/feature-placeholder-page').then(
            (module) => module.FeaturePlaceholderPage,
          ),
      },
      {
        path: 'activity',
        title: 'Activity',
        data: {
          featureKey: 'activity',
          eyebrow: 'Background work',
          heading: 'Activity',
          description:
            'The full resumable activity center arrives in Step 14. Dashboard filters are preserved in this route.',
        },
        loadComponent: () =>
          import('@app/features/placeholder/feature-placeholder-page').then(
            (module) => module.FeaturePlaceholderPage,
          ),
      },
      {
        path: 'lookup',
        title: 'Lookup',
        data: {
          featureKey: 'lookup',
          eyebrow: 'Global lookup',
          heading: 'Lookup',
          description:
            'Your search is preserved in the URL. Lookup results arrive with card management in Step 11.',
        },
        loadComponent: () =>
          import('@app/features/placeholder/feature-placeholder-page').then(
            (module) => module.FeaturePlaceholderPage,
          ),
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
