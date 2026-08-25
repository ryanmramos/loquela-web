import { ENVIRONMENT_NAMES, EnvironmentName, RuntimeConfig } from './runtime-config';

const CONFIG_PATH = '/config.json';
const LOCAL_CONFIG_PATH = '/config.local.json';
const CONFIG_ERROR_TITLE = 'Loquela configuration error';

export class RuntimeConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'RuntimeConfigError';
  }
}

export async function loadRuntimeConfig(
  fetchConfig: typeof fetch = globalThis.fetch,
): Promise<RuntimeConfig> {
  let response: Response;

  try {
    response = await fetchConfig(CONFIG_PATH, {
      cache: 'no-store',
      credentials: 'same-origin',
    });
  } catch {
    throw new RuntimeConfigError('Runtime configuration could not be loaded.');
  }

  if (!response.ok) {
    throw new RuntimeConfigError('Runtime configuration is unavailable.');
  }

  let value: unknown;
  try {
    value = await response.json();
  } catch {
    throw new RuntimeConfigError('Runtime configuration is not valid JSON.');
  }

  const config = parseRuntimeConfig(value);
  if (config.environmentName !== 'local' && config.environmentName !== 'test') {
    return config;
  }

  const localOverride = await loadLocalOverride(fetchConfig);
  if (localOverride === undefined) {
    return config;
  }
  if (!isRecord(value) || !isRecord(localOverride)) {
    throw new RuntimeConfigError('Local runtime configuration must be an object.');
  }

  return parseRuntimeConfig({ ...value, ...localOverride });
}

async function loadLocalOverride(fetchConfig: typeof fetch): Promise<unknown> {
  let response: Response;
  try {
    response = await fetchConfig(LOCAL_CONFIG_PATH, {
      cache: 'no-store',
      credentials: 'same-origin',
    });
  } catch {
    return undefined;
  }

  if (response.status === 404) {
    return undefined;
  }
  if (!response.ok) {
    throw new RuntimeConfigError('Local runtime configuration is unavailable.');
  }

  try {
    return await response.json();
  } catch {
    throw new RuntimeConfigError('Local runtime configuration is not valid JSON.');
  }
}

export function parseRuntimeConfig(value: unknown): RuntimeConfig {
  if (!isRecord(value)) {
    throw new RuntimeConfigError('Runtime configuration must be an object.');
  }

  const apiBasePath = requiredString(value, 'apiBasePath');
  const googleLoginPath = requiredString(value, 'googleLoginPath');
  const localLoginEnabled = value['localLoginEnabled'];
  const supportUrl = requiredString(value, 'supportUrl');
  const environmentName = requiredEnvironment(value['environmentName']);
  const telemetryEnabled = value['telemetryEnabled'];
  const releaseId = optionalReleaseId(value['releaseId']);
  const primeUiLicenseKey = optionalNonEmptyString(value['primeUiLicenseKey'], 'primeUiLicenseKey');

  if (!isSameOriginPath(apiBasePath, '/api/')) {
    throw new RuntimeConfigError('apiBasePath must be a same-origin /api path.');
  }
  if (!isSameOriginPath(googleLoginPath, '/api/')) {
    throw new RuntimeConfigError('googleLoginPath must be a same-origin /api path.');
  }
  if (!isSafeSupportUrl(supportUrl)) {
    throw new RuntimeConfigError('supportUrl must be a relative path or an HTTPS URL.');
  }
  if (typeof telemetryEnabled !== 'boolean') {
    throw new RuntimeConfigError('telemetryEnabled must be a boolean.');
  }
  if (typeof localLoginEnabled !== 'boolean') {
    throw new RuntimeConfigError('localLoginEnabled must be a boolean.');
  }
  if (localLoginEnabled && environmentName !== 'local' && environmentName !== 'test') {
    throw new RuntimeConfigError('localLoginEnabled is restricted to local and test environments.');
  }

  return {
    apiBasePath: trimTrailingSlash(apiBasePath),
    googleLoginPath,
    localLoginEnabled,
    supportUrl,
    environmentName,
    telemetryEnabled,
    releaseId,
    primeUiLicenseKey,
  };
}

function optionalNonEmptyString(value: unknown, key: string): string | undefined {
  if (value === undefined) return undefined;
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new RuntimeConfigError(`${key} must be a non-empty string when provided.`);
  }
  return value.trim();
}

function optionalReleaseId(value: unknown): string | undefined {
  if (value === undefined) return undefined;
  if (typeof value !== 'string' || !/^[A-Za-z0-9._-]{1,100}$/u.test(value)) {
    throw new RuntimeConfigError('releaseId must contain only safe release characters.');
  }
  return value;
}

export function renderRuntimeConfigError(documentRef: Document = document): void {
  documentRef.title = CONFIG_ERROR_TITLE;

  const main = documentRef.createElement('main');
  const heading = documentRef.createElement('h1');
  const explanation = documentRef.createElement('p');

  main.className = 'bootstrap-error';
  heading.textContent = 'Loquela could not start';
  explanation.textContent =
    'Public application configuration is unavailable or invalid. Try again later or contact support.';
  main.append(heading, explanation);

  // DOM nodes and textContent keep bootstrap failures independent from Angular and avoid HTML injection.
  documentRef.body.replaceChildren(main);
}

function requiredString(value: Readonly<Record<string, unknown>>, key: string): string {
  const field = value[key];
  if (typeof field !== 'string' || field.trim().length === 0) {
    throw new RuntimeConfigError(`${key} must be a non-empty string.`);
  }
  return field.trim();
}

function requiredEnvironment(value: unknown): EnvironmentName {
  if (!isEnvironmentName(value)) {
    throw new RuntimeConfigError('environmentName is not supported.');
  }
  return value;
}

function isEnvironmentName(value: unknown): value is EnvironmentName {
  return typeof value === 'string' && ENVIRONMENT_NAMES.some((name) => name === value);
}

function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isSameOriginPath(value: string, prefix: string): boolean {
  return value.startsWith(prefix) && !value.startsWith('//') && !value.includes('\\');
}

function isSafeSupportUrl(value: string): boolean {
  if (value.startsWith('/') && !value.startsWith('//') && !value.includes('\\')) {
    return true;
  }

  try {
    return new URL(value).protocol === 'https:';
  } catch {
    return false;
  }
}

function trimTrailingSlash(value: string): string {
  return value.length > 1 ? value.replace(/\/+$/, '') : value;
}
