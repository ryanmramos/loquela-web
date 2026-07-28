import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';

import { SessionFacade } from '@core/auth/session-facade';
import { InterfaceLanguage, TranslationService } from '@core/i18n/translation.service';
import { TranslatePipe } from '@core/i18n/translate.pipe';

@Component({
  selector: 'app-settings-page',
  imports: [TranslatePipe],
  template: `
    <section class="settings-page">
      <p class="eyebrow">{{ 'settings.eyebrow' | translate }}</p>
      <h1>{{ 'settings.title' | translate }}</h1>
      <section class="settings-card" aria-labelledby="interface-language-heading">
        <h2 id="interface-language-heading">{{ 'settings.interfaceLanguage' | translate }}</h2>
        <p>{{ 'settings.interfaceLanguageHelp' | translate }}</p>
        <label>
          <span>{{ 'settings.interfaceLanguage' | translate }}</span>
          <select [value]="translations.language()" (change)="changeLanguage($event)">
            <option value="en">{{ 'settings.english' | translate }}</option>
            <option value="es">{{ 'settings.spanish' | translate }}</option>
          </select>
        </label>
        @if (saving()) {
          <span>{{ 'settings.saving' | translate }}</span>
        } @else if (saved()) {
          <span>{{ 'settings.saved' | translate }}</span>
        }
      </section>
    </section>
  `,
  styles: `
    .settings-page {
      width: min(100%, 68rem);
      margin: 0 auto;
      padding: var(--space-8);
    }
    h1,
    h2 {
      font-family: var(--font-family-display);
    }
    h1 {
      font-size: var(--font-size-2xl);
    }
    .eyebrow {
      color: var(--color-primary);
      font-size: var(--font-size-xs);
      font-weight: 800;
      letter-spacing: 0.12em;
      text-transform: uppercase;
    }
    .settings-card {
      max-width: 38rem;
      padding: var(--space-6);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-xl);
      background: var(--color-surface-panel);
    }
    .settings-card p {
      color: var(--color-text-muted);
    }
    label {
      display: grid;
      gap: var(--space-2);
      font-weight: 700;
    }
    select {
      max-width: 18rem;
      padding: var(--space-3);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      background: var(--color-surface-raised);
      font: inherit;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsPage {
  readonly translations = inject(TranslationService);
  private readonly session = inject(SessionFacade);
  readonly saving = signal(false);
  readonly saved = signal(false);

  changeLanguage(event: Event): void {
    const language = (event.target as HTMLSelectElement).value as InterfaceLanguage;
    this.saving.set(true);
    this.saved.set(false);
    this.session.updateInterfaceLanguage(language).subscribe({
      next: () => {
        this.saving.set(false);
        this.saved.set(true);
      },
      error: () => {
        this.saving.set(false);
      },
    });
  }
}
