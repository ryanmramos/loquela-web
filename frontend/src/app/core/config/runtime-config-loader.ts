import { ENVIRONMENT_NAMES, EnvironmentName, RuntimeConfig } from './runtime-config';

const CONFIG_PATH = '/config.json';
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

  return parseRuntimeConfig(value);
}

export function parseRuntimeConfig(value: unknown): RuntimeConfig {
  if (!isRecord(value)) {
    throw new RuntimeConfigError('Runtime configuration must be an object.');
  }

  const apiBasePath = requiredString(value, 'apiBasePath');
  const googleLoginPath = requiredString(value, 'googleLoginPath');
  const supportUrl = requiredString(value, 'supportUrl');
  const environmentName = requiredEnvironment(value['environmentName']);
  const telemetryEnabled = value['telemetryEnabled'];

  if (!isSameOriginPath(apiBasePath, '/api/')) {
    throw new RuntimeConfigError('apiBasePath must be a same-origin /api path.');
  }
  if (!isSameOriginPath(googleLoginPath, '/oauth2/')) {
    throw new RuntimeConfigError('googleLoginPath must be a same-origin OAuth path.');
  }
  if (!isSafeSupportUrl(supportUrl)) {
    throw new RuntimeConfigError('supportUrl must be a relative path or an HTTPS URL.');
  }
  if (typeof telemetryEnabled !== 'boolean') {
    throw new RuntimeConfigError('telemetryEnabled must be a boolean.');
  }

  return {
    apiBasePath: trimTrailingSlash(apiBasePath),
    googleLoginPath,
    supportUrl,
    environmentName,
    telemetryEnabled,
  };
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
  if (typeof value !== 'string' || !ENVIRONMENT_NAMES.includes(value as EnvironmentName)) {
    throw new RuntimeConfigError('environmentName is not supported.');
  }
  return value as EnvironmentName;
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
