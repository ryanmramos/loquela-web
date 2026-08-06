import { Routes } from '@angular/router';
import { ConfirmationService } from 'primeng/api';

import { provideLoquelaUi } from '@core/theme/loquela-ui.providers';
import { adminGuard } from '@core/auth/session.guard';

export const authenticatedRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('@app/features/shell/authenticated-shell-page').then(
        (module) => module.AuthenticatedShellPage,
      ),
    providers: [ConfirmationService, provideLoquelaUi()],
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
      {
        path: 'admin',
        pathMatch: 'full',
        redirectTo: 'admin/access',
      },
      {
        path: 'admin/access',
        title: 'Access administration',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('@app/features/admin/access-admin-page').then((module) => module.AccessAdminPage),
      },
    ],
  },
];
