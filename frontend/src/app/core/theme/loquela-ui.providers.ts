import {
  CSP_NONCE,
  EnvironmentProviders,
  inject,
  makeEnvironmentProviders,
  provideEnvironmentInitializer,
} from '@angular/core';
import { PrimeNG } from 'primeng/config';
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
    if (result.valid) {
      primeNg._setVerified(true);
      return;
    }
    console.warn(`[PrimeUI] ${result.message}`);
  } catch {
    console.warn('[PrimeUI] License verification could not be completed.');
  }

  // PrimeNG's false state triggers a vendor banner that writes raw shadow.innerHTML, which
  // violates Loquela's required Trusted Types policy. Keep the warning and leave this state
  // unresolved unless verification succeeds.
}
