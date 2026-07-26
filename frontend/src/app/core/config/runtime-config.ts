import { EnvironmentProviders, InjectionToken, makeEnvironmentProviders } from '@angular/core';

export const ENVIRONMENT_NAMES = ['local', 'test', 'staging', 'production'] as const;

export type EnvironmentName = (typeof ENVIRONMENT_NAMES)[number];

/**
 * Public, deployment-specific values loaded before Angular starts.
 * Secrets must never be added because config.json is delivered to every browser.
 */
export interface RuntimeConfig {
  readonly apiBasePath: string;
  readonly googleLoginPath: string;
  readonly supportUrl: string;
  readonly environmentName: EnvironmentName;
  readonly telemetryEnabled: boolean;
}

export const RUNTIME_CONFIG = new InjectionToken<RuntimeConfig>('RUNTIME_CONFIG');

export function provideRuntimeConfig(config: RuntimeConfig): EnvironmentProviders {
  return makeEnvironmentProviders([{ provide: RUNTIME_CONFIG, useValue: config }]);
}
