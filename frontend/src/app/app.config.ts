import { ApplicationConfig, ErrorHandler, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { provideRouter, TitleStrategy, withInMemoryScrolling } from '@angular/router';

import { routes } from './app.routes';
import { RuntimeConfig, provideRuntimeConfig } from '@core/config/runtime-config';
import { AppErrorHandler } from '@core/errors/app-error-handler';
import { AppTitleStrategy } from '@core/layout/app-title-strategy';
import { apiCredentialsInterceptor } from '@core/http/api-credentials.interceptor';
import { correlationIdInterceptor } from '@core/http/correlation-id.interceptor';
import { requestDeadlineInterceptor } from '@core/http/request-deadline.interceptor';
import { apiProblemInterceptor } from '@core/http/api-problem.interceptor';
import { provideApiConfiguration } from '@api/generated/api-configuration';

export function createAppConfig(runtimeConfig: RuntimeConfig): ApplicationConfig {
  return {
    providers: [
      provideBrowserGlobalErrorListeners(),
      provideRuntimeConfig(runtimeConfig),
      // An empty generated-client root preserves same-origin /api paths from the contract.
      provideApiConfiguration(''),
      provideRouter(
        routes,
        withInMemoryScrolling({
          anchorScrolling: 'enabled',
          scrollPositionRestoration: 'enabled',
        }),
      ),
      provideHttpClient(
        withXsrfConfiguration({
          cookieName: 'XSRF-TOKEN',
          headerName: 'X-XSRF-TOKEN',
        }),
        withInterceptors([
          apiCredentialsInterceptor,
          correlationIdInterceptor,
          requestDeadlineInterceptor,
          apiProblemInterceptor,
        ]),
      ),
      { provide: TitleStrategy, useClass: AppTitleStrategy },
      { provide: ErrorHandler, useClass: AppErrorHandler },
    ],
  };
}
