import { bootstrapApplication } from '@angular/platform-browser';

import { createAppConfig } from '@app/app.config';
import { App } from '@app/app';
import { loadRuntimeConfig, renderRuntimeConfigError } from '@core/config/runtime-config-loader';

loadRuntimeConfig()
  .then((runtimeConfig) => bootstrapApplication(App, createAppConfig(runtimeConfig)))
  .catch(() => renderRuntimeConfigError());
