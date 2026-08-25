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
import { Router, RouterLink } from '@angular/router';
import { finalize, Observable } from 'rxjs';
import { InputText } from 'primeng/inputtext';
import { Textarea } from 'primeng/textarea';
import { Select } from 'primeng/select';

import { AcceptedPastedTextSource, AcceptedSource, SourceApi } from '@api/api-adapters/source-api';
import { Alert } from '@app/shared/components/alert/alert';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { FormField } from '@app/shared/components/form-field/form-field';
import { ApiProblemError } from '@core/errors/api-problem';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { TranslationService } from '@core/i18n/translation.service';
import { ActivityMonitor } from '@core/activity/activity-monitor';
import { AppIcon } from '@app/shared/components/icon/app-icon';
import { TimedTranscriptHelp } from '@app/shared/components/timed-transcript-help/timed-transcript-help';
import { SettingsApi } from '@api/api-adapters/settings-api';

type IntakeStatus = 'idle' | 'submitting' | 'error';
type TimelineOffsetUnit = 'hours' | 'minutes' | 'seconds';

@Component({
  selector: 'app-source-intake',
  imports: [
    Alert,
    AppIcon,
    ButtonDirective,
    FormField,
    InputText,
    ReactiveFormsModule,
    RouterLink,
    Select,
    Textarea,
    TranslatePipe,
    TimedTranscriptHelp,
  ],
  templateUrl: './source-intake.html',
  styleUrl: './source-intake.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SourceIntake {
  private static readonly MAX_UPLOAD_BYTES = 15 * 1024 * 1024;
  private static readonly MAX_TIMED_TRANSCRIPT_BYTES = 5 * 1024 * 1024;
  private readonly sourceApi = inject(SourceApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly translations = inject(TranslationService);
  private readonly activityMonitor = inject(ActivityMonitor);
  private readonly settingsApi = inject(SettingsApi);

  readonly status = signal<IntakeStatus>('idle');
  readonly errorMessage = signal<string | null>(null);
  readonly selectedFile = signal<File | null>(null);
  readonly selectedTranscriptFile = signal<File | null>(null);
  readonly supportedLanguages = signal<readonly string[]>([]);
  readonly transcriptLanguageOptions = computed(() =>
    this.supportedLanguages().map((language) => ({
      value: language,
      label: `${this.languageName(language)} (${language})`,
    })),
  );
  readonly pastedText = new FormGroup({
    text: new FormControl('', {
      nonNullable: true,
      validators: [(control) => Validators.required(control), Validators.maxLength(2_000_000)],
    }),
  });
  readonly transcript = new FormGroup({
    language: new FormControl('', {
      nonNullable: true,
      validators: [(control) => Validators.required(control)],
    }),
    originUrl: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(2048)] }),
    timelineOffsetHours: new FormControl(0, {
      nonNullable: true,
      validators: [(control) => Validators.required(control), Validators.min(0)],
    }),
    timelineOffsetMinutes: new FormControl(0, {
      nonNullable: true,
      validators: [
        (control) => Validators.required(control),
        Validators.min(0),
        Validators.max(59),
      ],
    }),
    timelineOffsetSeconds: new FormControl(0, {
      nonNullable: true,
      validators: [
        (control) => Validators.required(control),
        Validators.min(0),
        Validators.max(59),
      ],
    }),
  });

  constructor() {
    this.settingsApi
      .getLanguages()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ languagePair, supportedPairs }) => {
        this.transcript.controls.language.setValue(languagePair.sourceLanguage);
        this.supportedLanguages.set([
          ...new Set([
            languagePair.sourceLanguage,
            ...supportedPairs.map((pair) => pair.sourceLanguage),
          ]),
        ]);
      });
  }

  submitPastedText(): void {
    if (this.pastedText.invalid) {
      this.pastedText.markAllAsTouched();
      return;
    }
    this.submit(this.sourceApi.createPastedText(this.pastedText.getRawValue().text));
  }

  submitTranscript(): void {
    const file = this.selectedTranscriptFile();
    if (!file || this.transcript.invalid) {
      this.transcript.markAllAsTouched();
      if (!file)
        this.errorMessage.set(this.translations.translate('sourcesIntake.transcriptFileType'));
      return;
    }
    const value = this.transcript.getRawValue();
    this.submit(
      this.sourceApi.uploadTimedTranscript(
        file,
        value.language.trim(),
        value.originUrl,
        this.timelineOffsetMilliseconds(value),
      ),
    );
  }

  adjustTimelineOffset(event: WheelEvent, unit: TimelineOffsetUnit): void {
    event.preventDefault();
    this.changeTimelineOffset(unit, event.deltaY < 0 ? 1 : -1);
  }

  adjustTimelineOffsetKeyboard(event: KeyboardEvent, unit: TimelineOffsetUnit): void {
    if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return;
    event.preventDefault();
    this.changeTimelineOffset(unit, event.key === 'ArrowUp' ? 1 : -1);
  }

  private changeTimelineOffset(unit: TimelineOffsetUnit, delta: number): void {
    const control =
      unit === 'hours'
        ? this.transcript.controls.timelineOffsetHours
        : unit === 'minutes'
          ? this.transcript.controls.timelineOffsetMinutes
          : this.transcript.controls.timelineOffsetSeconds;
    const maximum = unit === 'hours' ? Number.MAX_SAFE_INTEGER : 59;
    const value = Math.min(maximum, Math.max(0, (control.value ?? 0) + delta));
    control.setValue(value);
  }

  private timelineOffsetMilliseconds(value: {
    timelineOffsetHours: number;
    timelineOffsetMinutes: number;
    timelineOffsetSeconds: number;
  }): number {
    return Math.round(
      (Math.max(0, value.timelineOffsetHours) * 3600 +
        Math.min(59, Math.max(0, value.timelineOffsetMinutes)) * 60 +
        Math.min(59, Math.max(0, value.timelineOffsetSeconds))) *
        1000,
    );
  }

  selectTranscriptFile(event: Event): void {
    if (!(event.currentTarget instanceof HTMLInputElement)) return;
    const input = event.currentTarget;
    const file = input.files?.item(0) ?? null;
    this.errorMessage.set(null);
    if (!file) {
      this.selectedTranscriptFile.set(null);
      return;
    }
    if (file.size > SourceIntake.MAX_TIMED_TRANSCRIPT_BYTES) {
      this.selectedTranscriptFile.set(null);
      this.errorMessage.set(this.translations.translate('sourcesIntake.transcriptTooLarge'));
      input.value = '';
      return;
    }
    const lower = file.name.toLowerCase();
    if (!lower.endsWith('.vtt') && !lower.endsWith('.srt')) {
      this.selectedTranscriptFile.set(null);
      this.errorMessage.set(this.translations.translate('sourcesIntake.transcriptFileType'));
      input.value = '';
      return;
    }
    const mediaType = file.type || (lower.endsWith('.vtt') ? 'text/vtt' : 'application/x-subrip');
    this.selectedTranscriptFile.set(
      file.type ? file : new File([file], file.name, { type: mediaType }),
    );
  }

  selectFile(event: Event): void {
    if (!(event.currentTarget instanceof HTMLInputElement)) return;
    const input = event.currentTarget;
    const file = input.files?.item(0) ?? null;
    this.errorMessage.set(null);
    if (!file) {
      this.selectedFile.set(null);
      return;
    }
    if (file.size > SourceIntake.MAX_UPLOAD_BYTES) {
      this.selectedFile.set(null);
      this.errorMessage.set(this.translations.translate('sourcesIntake.fileTooLarge'));
      input.value = '';
      return;
    }
    const mediaType = file.type || this.mediaTypeFromName(file.name);
    if (!['application/pdf', 'text/plain', 'application/epub+zip'].includes(mediaType)) {
      this.selectedFile.set(null);
      this.errorMessage.set(this.translations.translate('sourcesIntake.fileType'));
      input.value = '';
      return;
    }
    // Some browsers omit EPUB/TXT MIME types; normalize the File so the
    // server can compare the multipart declaration with the upload intent.
    this.selectedFile.set(
      file.type === mediaType ? file : new File([file], file.name, { type: mediaType }),
    );
  }

  submitFile(): void {
    const file = this.selectedFile();
    if (!file) {
      this.errorMessage.set(this.translations.translate('sourcesIntake.fileType'));
      return;
    }
    this.submit(this.sourceApi.upload(file));
  }

  private submit(request: Observable<AcceptedSource | AcceptedPastedTextSource>): void {
    if (this.status() === 'submitting') return;
    this.status.set('submitting');
    this.errorMessage.set(null);
    request
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (this.status() === 'submitting') this.status.set('idle');
        }),
      )
      .subscribe({
        next: (source) => {
          this.activityMonitor.refreshBaseline();
          void this.router.navigate(['/app/sources', source.reference]);
        },
        error: (error: unknown) => {
          this.status.set('error');
          this.errorMessage.set(
            error instanceof ApiProblemError
              ? (error.problem.detail ?? error.problem.title)
              : this.translations.translate('sourcesIntake.submitError'),
          );
        },
      });
  }

  private mediaTypeFromName(name: string): string {
    const normalized = name.toLowerCase();
    if (normalized.endsWith('.pdf')) return 'application/pdf';
    if (normalized.endsWith('.txt')) return 'text/plain';
    if (normalized.endsWith('.epub')) return 'application/epub+zip';
    return '';
  }

  languageName(tag: string): string {
    try {
      const locale = this.translations.language();
      const name = new Intl.DisplayNames([locale], { type: 'language' }).of(tag) ?? tag;
      return name.charAt(0).toLocaleUpperCase(locale) + name.slice(1);
    } catch {
      return tag;
    }
  }
}
