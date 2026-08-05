import {
  ApplicationConfig,
  ErrorHandler,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
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
import { sessionExpirationInterceptor } from '@core/auth/session-expiration.interceptor';
import { ThemeService } from '@core/theme/theme.service';
import { WebVitalsTelemetry } from '@core/telemetry/web-vitals-telemetry';

export function createAppConfig(runtimeConfig: RuntimeConfig): ApplicationConfig {
  return {
    providers: [
      provideBrowserGlobalErrorListeners(),
      provideAppInitializer(() => inject(ThemeService).initialize()),
      provideAppInitializer(() => inject(WebVitalsTelemetry).start()),
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
          sessionExpirationInterceptor,
          apiProblemInterceptor,
        ]),
      ),
      { provide: TitleStrategy, useClass: AppTitleStrategy },
      { provide: ErrorHandler, useClass: AppErrorHandler },
    ],
  };
}
