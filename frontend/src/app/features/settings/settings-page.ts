import { ViewportScroller } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';

import { BrowserSettings, LanguagePair, SettingsApi } from '@api/api-adapters/settings-api';
import { SessionFacade } from '@core/auth/session-facade';
import { RUNTIME_CONFIG } from '@core/config/runtime-config';
import {
  INTERFACE_LANGUAGE_OPTIONS,
  InterfaceLanguage,
  TranslationService,
} from '@core/i18n/translation.service';
import { Alert } from '@app/shared/components/alert/alert';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { Skeleton } from '@app/shared/components/skeleton/skeleton';
import { UiState } from '@app/shared/components/ui-state/ui-state';
import { TranslatePipe } from '@core/i18n/translate.pipe';

type Section = 'profile' | 'accounts' | 'language' | 'delivery' | 'export' | 'privacy';

@Component({
  selector: 'app-settings-page',
  imports: [
    Alert,
    ButtonDirective,
    InputText,
    FormsModule,
    ReactiveFormsModule,
    Skeleton,
    Select,
    UiState,
    TranslatePipe,
  ],
  templateUrl: './settings-page.html',
  styleUrl: './settings-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsPage {
  readonly interfaceLanguages = INTERFACE_LANGUAGE_OPTIONS;
  readonly interfaceLanguageOptions = computed(() =>
    this.interfaceLanguages.map((language) => ({
      code: language.code,
      label: this.translations.translate(language.label),
    })),
  );
  readonly translations = inject(TranslationService);
  private readonly session = inject(SessionFacade);
  private readonly settingsApi = inject(SettingsApi);
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly router = inject(Router);
  private readonly viewportScroller = inject(ViewportScroller);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly settings = signal<BrowserSettings | null>(null);
  readonly activeSection = signal<Section>('profile');
  readonly interfaceSaving = signal(false);
  readonly interfaceError = signal(false);
  readonly languageSaving = signal(false);
  readonly languageSaved = signal(false);
  readonly languageError = signal(false);
  readonly captionSaving = signal(false);
  readonly captionSaved = signal(false);
  readonly captionError = signal(false);
  readonly exportSaving = signal(false);
  readonly exportSaved = signal(false);
  readonly exportError = signal(false);
  readonly deletionSaving = signal(false);
  readonly deletionError = signal<'reauth' | 'request' | null>(null);
  readonly deletionStarted = signal(false);
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
  readonly captionForm = new FormGroup({
    automaticCaptionFallback: new FormControl(true, { nonNullable: true }),
  });
  readonly exportForm = new FormGroup({
    exportDeckPrefix: new FormControl('', {
      nonNullable: true,
      validators: Validators.maxLength(100),
    }),
    exportIncludeProvenance: new FormControl(true, { nonNullable: true }),
  });
  readonly deletionForm = new FormGroup({
    acknowledged: new FormControl(false, {
      nonNullable: true,
      validators: (control) => Validators.requiredTrue(control),
    }),
  });

  readonly reauthenticateUrl = `${this.config.googleLoginPath}?returnTo=${encodeURIComponent(
    '/app/settings?section=privacy',
  )}&reauthenticate=true`;

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.settingsApi
      .get()
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (settings) => this.applySettings(settings),
        error: () => this.loadError.set(true),
      });
  }

  selectSection(section: Section): void {
    this.activeSection.set(section);
    // Keep the section heading below the shell's sticky top bar after in-page navigation.
    this.viewportScroller.setOffset([0, 88]);
    this.viewportScroller.scrollToAnchor(section);
  }

  changeInterfaceLanguage(value: string): void {
    const language = value as InterfaceLanguage;
    this.interfaceSaving.set(true);
    this.interfaceError.set(false);
    this.session
      .updateInterfaceLanguage(language)
      .pipe(
        finalize(() => this.interfaceSaving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => undefined,
        error: () => this.interfaceError.set(true),
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

  saveLanguages(): void {
    const pair = this.languageForm.getRawValue();
    if (
      this.languageForm.invalid ||
      !this.supportedPairs().some(
        (supported) =>
          supported.sourceLanguage === pair.sourceLanguage &&
          supported.definitionLanguage === pair.definitionLanguage,
      )
    ) {
      this.languageError.set(true);
      return;
    }
    this.savePreferences('language');
  }

  saveCaptions(): void {
    this.savePreferences('caption');
  }

  saveExports(): void {
    if (this.exportForm.invalid) {
      this.exportError.set(true);
      return;
    }
    this.savePreferences('export');
  }

  startDeletion(): void {
    this.deletionStarted.set(true);
    this.deletionError.set(null);
  }

  cancelDeletion(): void {
    this.deletionStarted.set(false);
    this.deletionError.set(null);
    this.deletionForm.reset({ acknowledged: false });
  }

  deleteAccount(): void {
    if (this.deletionForm.invalid) {
      this.deletionForm.markAllAsTouched();
      return;
    }
    this.deletionSaving.set(true);
    this.deletionError.set(null);
    this.settingsApi
      .deleteAccount('DELETE MY ACCOUNT')
      .pipe(
        finalize(() => this.deletionSaving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.session.markDeleting();
          void this.router.navigate(['/account-deleting'], { replaceUrl: true });
        },
        error: (error: { problem?: { code?: string } }) =>
          this.deletionError.set(
            error.problem?.code === 'RECENT_AUTHENTICATION_REQUIRED' ? 'reauth' : 'request',
          ),
      });
  }

  sourceLanguages(): readonly string[] {
    return [...new Set(this.supportedPairs().map((pair) => pair.sourceLanguage))];
  }

  sourceLanguageOptions(): { value: string; label: string }[] {
    return this.sourceLanguages().map((language) => ({
      value: language,
      label: `${this.languageName(language)} (${language})`,
    }));
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

  definitionLanguageOptions(sourceLanguage: string): { value: string; label: string }[] {
    return this.definitionLanguages(sourceLanguage).map((language) => ({
      value: language,
      label: `${this.languageName(language)} (${language})`,
    }));
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

  formatDate(value: string | null): string {
    return value
      ? new Intl.DateTimeFormat(this.translations.language()).format(new Date(value))
      : '—';
  }

  private savePreferences(section: 'language' | 'caption' | 'export'): void {
    const flags = {
      language: [this.languageSaving, this.languageSaved, this.languageError],
      caption: [this.captionSaving, this.captionSaved, this.captionError],
      export: [this.exportSaving, this.exportSaved, this.exportError],
    } as const;
    const [saving, saved, error] = flags[section];
    saving.set(true);
    saved.set(false);
    error.set(false);
    this.settingsApi
      .update({
        ...this.languageForm.getRawValue(),
        ...this.captionForm.getRawValue(),
        ...this.exportForm.getRawValue(),
      })
      .pipe(
        finalize(() => saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (settings) => {
          this.applySettings(settings, section);
          saved.set(true);
        },
        error: () => error.set(true),
      });
  }

  private applySettings(
    settings: BrowserSettings,
    savedSection?: 'language' | 'caption' | 'export',
  ): void {
    this.settings.set(settings);
    this.supportedPairs.set(settings.supportedPairs);
    if (!savedSection || savedSection === 'language') {
      this.languageForm.reset(settings.languagePair);
    }
    if (!savedSection || savedSection === 'caption') {
      this.captionForm.reset({ automaticCaptionFallback: settings.automaticCaptionFallback });
    }
    if (!savedSection || savedSection === 'export') {
      this.exportForm.reset({
        exportDeckPrefix: settings.exportDeckPrefix,
        exportIncludeProvenance: settings.exportIncludeProvenance,
      });
    }
  }
}
