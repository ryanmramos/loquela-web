import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ConfirmationService } from 'primeng/api';
import { InputText } from 'primeng/inputtext';

import { SourceDetails as SourceDetailsModel } from '@api/api-adapters/source-api';
import { Alert } from '@app/shared/components/alert/alert';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { FormField } from '@app/shared/components/form-field/form-field';
import { StatusBadge, StatusTone } from '@app/shared/components/status-badge/status-badge';
import { UiState } from '@app/shared/components/ui-state/ui-state';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { TranslationService } from '@core/i18n/translation.service';
import { AppIcon } from '@app/shared/components/icon/app-icon';

import { SourceDetailsState } from './source-details-state';

@Component({
  selector: 'app-source-details',
  imports: [
    Alert,
    AppIcon,
    ButtonDirective,
    DecimalPipe,
    FormField,
    InputText,
    RouterLink,
    StatusBadge,
    UiState,
    TranslatePipe,
  ],
  providers: [SourceDetailsState],
  templateUrl: './source-details.html',
  styleUrl: './source-details.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SourceDetails {
  private readonly route = inject(ActivatedRoute);
  private readonly confirmations = inject(ConfirmationService);
  readonly state = inject(SourceDetailsState);
  private readonly translations = inject(TranslationService);

  constructor() {
    this.state.load(this.route.snapshot.paramMap.get('sourceReference') ?? '');
  }

  rename(name: string): void {
    this.state.rename(name);
  }

  confirmDelete(): void {
    this.confirmations.confirm({
      header: this.translations.translate('sourceDetails.confirmDeleteTitle'),
      message: this.translations.translate('sourceDetails.confirmDeleteDescription'),
      closable: false,
      acceptLabel: this.translations.translate('sourceDetails.deleteSource'),
      rejectLabel: this.translations.translate('common.cancel'),
      acceptButtonProps: { severity: 'danger', styleClass: 'loquela-confirm-dialog__accept' },
      rejectButtonProps: {
        outlined: true,
        severity: 'secondary',
        styleClass: 'loquela-confirm-dialog__reject',
      },
      accept: () => this.state.delete(),
    });
  }

  kindLabel(kind: SourceDetailsModel['kind']): string {
    return (
      {
        PASTED_TEXT: this.translations.translate('sourceDetails.pastedText'),
        TXT: this.translations.translate('sourceDetails.txt'),
        PDF: this.translations.translate('sourceDetails.pdf'),
        EPUB: this.translations.translate('sourceDetails.epub'),
        YOUTUBE_VIDEO: this.translations.translate('sourceDetails.youtube'),
      } as const
    )[kind];
  }

  statusLabel(status: SourceDetailsModel['status']): string {
    const keys = {
      READY: 'sourceDetails.statusReady',
      RECEIVED: 'sourceDetails.statusReceived',
      FETCHING: 'sourceDetails.statusFetching',
      STORED: 'sourceDetails.statusStored',
      EXTRACTING: 'sourceDetails.statusExtracting',
      INDEXING: 'sourceDetails.statusIndexing',
      FAILED: 'sourceDetails.statusFailed',
      DELETING: 'sourceDetails.statusDeleting',
      DELETED: 'sourceDetails.statusDeleted',
    } as const;
    return this.translations.translate(keys[status]);
  }

  statusTone(status: SourceDetailsModel['status']): StatusTone {
    if (status === 'READY') return 'success';
    if (status === 'FAILED') return 'danger';
    if (status === 'DELETING') return 'warning';
    return 'info';
  }

  failureGuidance(code: string | undefined): string {
    const keys = {
      PDF_NEEDS_OCR: 'sourceDetails.failurePdfOcr',
      ENCRYPTED_DOCUMENT: 'sourceDetails.failureEncrypted',
      INVALID_TEXT_ENCODING: 'sourceDetails.failureEncoding',
      NO_VIDEO_CAPTIONS: 'sourceDetails.failureCaptions',
      DOCUMENT_HAS_NO_TEXT: 'sourceDetails.failureNoText',
    } as const;
    return this.translations.translate(
      keys[code as keyof typeof keys] ?? 'sourceDetails.failureGeneric',
    );
  }

  languageName(tag: string): string {
    try {
      const name = new Intl.DisplayNames([tag], { type: 'language' }).of(tag) ?? tag;
      return name.charAt(0).toLocaleUpperCase(tag) + name.slice(1);
    } catch {
      return tag;
    }
  }

  formatBytes(bytes: number | undefined): string {
    if (bytes === undefined) return '—';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
  }

  formatDate(value: string): string {
    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? '—'
      : new Intl.DateTimeFormat(this.translations.language(), {
          dateStyle: 'medium',
          timeStyle: 'short',
        }).format(date);
  }
}
