import { Routes } from '@angular/router';
import { ConfirmationService } from 'primeng/api';

import { provideLoquelaUi } from '@core/theme/loquela-ui.providers';

export const demoRoutes: Routes = [
  {
    path: '',
    title: 'Try Loquela',
    providers: [ConfirmationService, provideLoquelaUi()],
    loadComponent: () => import('@app/features/demo/demo-page').then((module) => module.DemoPage),
  },
];
