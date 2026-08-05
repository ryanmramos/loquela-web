import { describe, expect, it, vi } from 'vitest';

import {
  loadRuntimeConfig,
  parseRuntimeConfig,
  renderRuntimeConfigError,
  RuntimeConfigError,
} from './runtime-config-loader';

const validConfig = {
  apiBasePath: '/api/v1',
  googleLoginPath: '/api/v1/auth/google',
  localLoginEnabled: true,
  supportUrl: '/support',
  environmentName: 'local',
  telemetryEnabled: false,
} as const;

describe('runtime configuration', () => {
  it('loads and validates public configuration without caching it', async () => {
    const fetchConfig = vi.fn<typeof fetch>().mockImplementation((input) =>
      Promise.resolve(
        input === '/config.local.json'
          ? new Response(null, { status: 404 })
          : new Response(JSON.stringify(validConfig), {
              headers: { 'Content-Type': 'application/json' },
              status: 200,
            }),
      ),
    );

    await expect(loadRuntimeConfig(fetchConfig)).resolves.toEqual(validConfig);
    expect(fetchConfig).toHaveBeenCalledWith('/config.json', {
      cache: 'no-store',
      credentials: 'same-origin',
    });
  });

  it('merges an ignored local override in local environments', async () => {
    const fetchConfig = vi
      .fn<typeof fetch>()
      .mockImplementation((input) =>
        Promise.resolve(
          new Response(
            JSON.stringify(
              input === '/config.local.json'
                ? { primeUiLicenseKey: 'PrimeUI-local-license' }
                : validConfig,
            ),
            { headers: { 'Content-Type': 'application/json' }, status: 200 },
          ),
        ),
      );

    await expect(loadRuntimeConfig(fetchConfig)).resolves.toEqual({
      ...validConfig,
      primeUiLicenseKey: 'PrimeUI-local-license',
    });
  });

  it('does not request the local override in production', async () => {
    const productionConfig = {
      ...validConfig,
      environmentName: 'production',
      localLoginEnabled: false,
    } as const;
    const fetchConfig = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify(productionConfig), {
        headers: { 'Content-Type': 'application/json' },
        status: 200,
      }),
    );

    await expect(loadRuntimeConfig(fetchConfig)).resolves.toEqual(productionConfig);
    expect(fetchConfig).not.toHaveBeenCalledWith('/config.local.json', expect.anything());
  });

  it('rejects a missing required value', () => {
    expect(() => parseRuntimeConfig({ ...validConfig, apiBasePath: '' })).toThrow(
      RuntimeConfigError,
    );
  });

  it('rejects a cross-origin API base URL', () => {
    expect(() =>
      parseRuntimeConfig({
        ...validConfig,
        apiBasePath: 'https://api.example.com/api/v1',
      }),
    ).toThrow('apiBasePath must be a same-origin /api path.');
  });

  it('rejects local login outside local and test environments', () => {
    expect(() =>
      parseRuntimeConfig({
        ...validConfig,
        environmentName: 'production',
      }),
    ).toThrow('localLoginEnabled is restricted to local and test environments.');
  });

  it('renders a safe bootstrap error without inserting configuration details', () => {
    renderRuntimeConfigError(document);

    expect(document.title).toBe('Loquela configuration error');
    expect(document.querySelector('h1')?.textContent).toBe('Loquela could not start');
    expect(document.body.textContent).not.toContain('apiBasePath');
  });
});
