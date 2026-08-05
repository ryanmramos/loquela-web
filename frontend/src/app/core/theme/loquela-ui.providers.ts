import {
  CSP_NONCE,
  EnvironmentProviders,
  inject,
  makeEnvironmentProviders,
  provideEnvironmentInitializer,
} from '@angular/core';
import { PrimeNG } from 'primeng/config';
import { showInvalidLicenseBanner } from 'primeng/license';
import { registerLicense, verifyLicense } from '@primeui/license-manager';

import { RUNTIME_CONFIG } from '@core/config/runtime-config';
import { LoquelaPreset } from './loquela-preset';

const PRIMENG_RELEASE_DATE = '2026-07-15';

export function provideLoquelaUi(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideEnvironmentInitializer(() => {
      const primeNg = inject(PrimeNG);
      const licenseKey = inject(RUNTIME_CONFIG).primeUiLicenseKey;
      const nonce = inject(CSP_NONCE, { optional: true }) ?? undefined;

      primeNg.setConfig({
        csp: { nonce },
        ripple: true,
        theme: {
          preset: LoquelaPreset,
          options: {
            darkModeSelector: "[data-theme='dark']",
            cssLayer: {
              name: 'primeng',
              order: 'reset, primeng, app',
            },
          },
        },
      });

      if (licenseKey) {
        registerLicense({ primeui: licenseKey });
      }
      void verifyPrimeUiLicense(primeNg);
    }),
  ]);
}

async function verifyPrimeUiLicense(primeNg: PrimeNG): Promise<void> {
  try {
    const result = await verifyLicense('primeui', { releaseDate: PRIMENG_RELEASE_DATE });
    primeNg._setVerified(result.valid);
    if (result.valid) return;
    console.warn(`[PrimeUI] ${result.message}`);
  } catch {
    primeNg._setVerified(false);
    console.warn('[PrimeUI] License verification could not be completed.');
  }

  // Keep PrimeNG's documented missing/invalid-license behavior when configured lazily.
  if (!primeNg.verified()) {
    try {
      showInvalidLicenseBanner();
    } catch {
      // PrimeUI 22's notice uses TrustedHTML-incompatible shadow markup. The console warning
      // above remains available without weakening Loquela's required Trusted Types policy.
    }
  }
}
