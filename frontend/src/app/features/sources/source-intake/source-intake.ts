import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, Observable } from 'rxjs';
import { InputText } from 'primeng/inputtext';
import { Textarea } from 'primeng/textarea';

import { AcceptedPastedTextSource, AcceptedSource, SourceApi } from '@api/api-adapters/source-api';
import { Alert } from '@app/shared/components/alert/alert';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { FormField } from '@app/shared/components/form-field/form-field';
import { ApiProblemError } from '@core/errors/api-problem';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { TranslationService } from '@core/i18n/translation.service';
import { ActivityMonitor } from '@core/activity/activity-monitor';
import { AppIcon } from '@app/shared/components/icon/app-icon';

type IntakeStatus = 'idle' | 'submitting' | 'error';

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
    Textarea,
    TranslatePipe,
  ],
  templateUrl: './source-intake.html',
  styleUrl: './source-intake.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SourceIntake {
  private static readonly MAX_UPLOAD_BYTES = 15 * 1024 * 1024;
  private readonly sourceApi = inject(SourceApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly translations = inject(TranslationService);
  private readonly activityMonitor = inject(ActivityMonitor);

  readonly status = signal<IntakeStatus>('idle');
  readonly errorMessage = signal<string | null>(null);
  readonly selectedFile = signal<File | null>(null);
  readonly pastedText = new FormGroup({
    text: new FormControl('', {
      nonNullable: true,
      validators: [(control) => Validators.required(control), Validators.maxLength(2_000_000)],
    }),
  });
  readonly youtube = new FormGroup({
    url: new FormControl('', {
      nonNullable: true,
      validators: [(control) => Validators.required(control), Validators.maxLength(2048)],
    }),
  });

  submitPastedText(): void {
    if (this.pastedText.invalid) {
      this.pastedText.markAllAsTouched();
      return;
    }
    this.submit(this.sourceApi.createPastedText(this.pastedText.getRawValue().text));
  }

  submitYouTube(): void {
    if (this.youtube.invalid) {
      this.youtube.markAllAsTouched();
      return;
    }
    this.submit(this.sourceApi.createYouTube(this.youtube.getRawValue().url));
  }

  selectFile(event: Event): void {
    const input = event.target as HTMLInputElement;
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
}
