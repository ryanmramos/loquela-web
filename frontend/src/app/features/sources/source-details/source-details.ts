import { Dialog, DialogModule } from '@angular/cdk/dialog';
import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { filter } from 'rxjs';

import { SourceDetails as SourceDetailsModel } from '@api/api-adapters/source-api';
import { Alert } from '@app/shared/components/alert/alert';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import {
  ConfirmDialog,
  ConfirmDialogData,
} from '@app/shared/components/confirm-dialog/confirm-dialog';
import { FormField } from '@app/shared/components/form-field/form-field';
import { StatusBadge, StatusTone } from '@app/shared/components/status-badge/status-badge';
import { UiState } from '@app/shared/components/ui-state/ui-state';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { TranslationService } from '@core/i18n/translation.service';

import { SourceDetailsState } from './source-details-state';

@Component({
  selector: 'app-source-details',
  imports: [
    Alert,
    ButtonDirective,
    DecimalPipe,
    DialogModule,
    FormField,
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
  private readonly dialog = inject(Dialog);
  readonly state = inject(SourceDetailsState);
  private readonly translations = inject(TranslationService);

  constructor() {
    this.state.load(this.route.snapshot.paramMap.get('sourceReference') ?? '');
  }

  rename(name: string): void {
    this.state.rename(name);
  }

  confirmDelete(): void {
    this.dialog
      .open<boolean, ConfirmDialogData>(ConfirmDialog, {
        data: {
          title: this.translations.translate('sourceDetails.confirmDeleteTitle'),
          description: this.translations.translate('sourceDetails.confirmDeleteDescription'),
          confirmLabel: this.translations.translate('sourceDetails.deleteSource'),
        },
        ariaLabel: this.translations.translate('sourceDetails.confirmDeleteAria'),
        autoFocus: 'button',
        restoreFocus: true,
      })
      .closed.pipe(filter(Boolean))
      .subscribe(() => this.state.delete());
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
