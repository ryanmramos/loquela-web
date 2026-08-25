import { DOCUMENT } from '@angular/common';
import { inject, Injectable, signal } from '@angular/core';

import { BrowserStorage } from '@core/storage/browser-storage';

export type ColorTheme = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly storage = inject(BrowserStorage);
  private readonly currentTheme = signal<ColorTheme>('light');
  private systemTheme?: MediaQueryList;
  private hasExplicitPreference = false;

  readonly theme = this.currentTheme.asReadonly();

  initialize(): void {
    const stored = this.storedTheme();
    this.hasExplicitPreference = stored !== undefined;
    this.systemTheme = this.document.defaultView?.matchMedia?.('(prefers-color-scheme: dark)');
    this.apply(stored ?? this.systemPreference());
    this.systemTheme?.addEventListener('change', (event) => {
      if (!this.hasExplicitPreference) {
        this.apply(event.matches ? 'dark' : 'light');
      }
    });
  }

  toggle(): void {
    const next = this.currentTheme() === 'dark' ? 'light' : 'dark';
    this.hasExplicitPreference = true;
    this.storage.write('color-theme', next);
    this.apply(next);
  }

  private systemPreference(): ColorTheme {
    return this.systemTheme?.matches ? 'dark' : 'light';
  }

  private storedTheme(): ColorTheme | undefined {
    const stored = this.storage.read('color-theme');
    return stored === 'light' || stored === 'dark' ? stored : undefined;
  }

  private apply(theme: ColorTheme): void {
    this.currentTheme.set(theme);
    this.document.documentElement.dataset['theme'] = theme;
  }
}
