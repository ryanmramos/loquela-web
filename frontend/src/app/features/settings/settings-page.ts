import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { LanguagePair, SettingsApi } from '@api/api-adapters/settings-api';
import { SessionFacade } from '@core/auth/session-facade';
import { InterfaceLanguage, TranslationService } from '@core/i18n/translation.service';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { Alert } from '@app/shared/components/alert/alert';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { Skeleton } from '@app/shared/components/skeleton/skeleton';
import { UiState } from '@app/shared/components/ui-state/ui-state';

@Component({
  selector: 'app-settings-page',
  imports: [Alert, ButtonDirective, ReactiveFormsModule, Skeleton, TranslatePipe, UiState],
  templateUrl: './settings-page.html',
  styleUrl: './settings-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsPage {
  readonly translations = inject(TranslationService);
  private readonly session = inject(SessionFacade);
  private readonly settingsApi = inject(SettingsApi);

  readonly interfaceSaving = signal(false);
  readonly interfaceSaved = signal(false);
  readonly interfaceError = signal(false);
  readonly languageLoading = signal(true);
  readonly languageLoadError = signal(false);
  readonly languageSaving = signal(false);
  readonly languageSaved = signal(false);
  readonly languageError = signal(false);
  readonly supportedPairs = signal<readonly LanguagePair[]>([]);

  readonly languageForm = new FormGroup({
    sourceLanguage: new FormControl('', {
      nonNullable: true,
      validators: (control) => Validators.required(control),
    }),
    definitionLanguage: new FormControl('', {
      nonNullable: true,
      validators: (control) => Validators.required(control),
    }),
  });

  constructor() {
    this.loadLanguageSettings();
  }

  changeInterfaceLanguage(event: Event): void {
    const language = (event.target as HTMLSelectElement).value as InterfaceLanguage;
    this.interfaceSaving.set(true);
    this.interfaceSaved.set(false);
    this.interfaceError.set(false);
    this.session.updateInterfaceLanguage(language).subscribe({
      next: () => {
        this.interfaceSaving.set(false);
        this.interfaceSaved.set(true);
      },
      error: () => {
        this.interfaceSaving.set(false);
        this.interfaceError.set(true);
      },
    });
  }

  loadLanguageSettings(): void {
    this.languageLoading.set(true);
    this.languageLoadError.set(false);
    this.settingsApi.getLanguages().subscribe({
      next: (settings) => {
        this.supportedPairs.set(settings.supportedPairs);
        this.languageForm.reset(settings.languagePair);
        this.languageLoading.set(false);
      },
      error: () => {
        this.languageLoading.set(false);
        this.languageLoadError.set(true);
      },
    });
  }

  sourceLanguageChanged(): void {
    this.languageSaved.set(false);
    this.languageError.set(false);
    const definitions = this.definitionLanguages(this.languageForm.controls.sourceLanguage.value);
    if (!definitions.includes(this.languageForm.controls.definitionLanguage.value)) {
      this.languageForm.controls.definitionLanguage.setValue(definitions[0] ?? '');
    }
  }

  languageSelectionChanged(): void {
    this.languageSaved.set(false);
    this.languageError.set(false);
  }

  saveLanguageSettings(): void {
    const languagePair = this.languageForm.getRawValue();
    if (
      this.languageForm.invalid ||
      !this.supportedPairs().some(
        (pair) =>
          pair.sourceLanguage === languagePair.sourceLanguage &&
          pair.definitionLanguage === languagePair.definitionLanguage,
      )
    ) {
      this.languageError.set(true);
      return;
    }

    this.languageSaving.set(true);
    this.languageSaved.set(false);
    this.languageError.set(false);
    this.languageForm.disable();
    this.settingsApi.updateLanguages(languagePair).subscribe({
      next: (settings) => {
        this.supportedPairs.set(settings.supportedPairs);
        this.languageForm.enable();
        this.languageForm.reset(settings.languagePair);
        this.languageSaving.set(false);
        this.languageSaved.set(true);
      },
      error: () => {
        this.languageForm.enable();
        this.languageSaving.set(false);
        this.languageError.set(true);
      },
    });
  }

  sourceLanguages(): readonly string[] {
    return [...new Set(this.supportedPairs().map((pair) => pair.sourceLanguage))];
  }

  definitionLanguages(sourceLanguage: string): readonly string[] {
    return [
      ...new Set(
        this.supportedPairs()
          .filter((pair) => pair.sourceLanguage === sourceLanguage)
          .map((pair) => pair.definitionLanguage),
      ),
    ];
  }

  languageName(tag: string): string {
    try {
      return (
        new Intl.DisplayNames([this.translations.language()], { type: 'language' }).of(tag) ?? tag
      );
    } catch {
      return tag;
    }
  }
}
