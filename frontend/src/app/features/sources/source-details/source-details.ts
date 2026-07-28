import { Dialog, DialogModule } from '@angular/cdk/dialog';
import { DatePipe, DecimalPipe } from '@angular/common';
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

import { SourceDetailsState } from './source-details-state';

@Component({
  selector: 'app-source-details',
  imports: [
    Alert,
    ButtonDirective,
    DatePipe,
    DecimalPipe,
    DialogModule,
    FormField,
    RouterLink,
    StatusBadge,
    UiState,
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
          title: 'Delete this source?',
          description:
            'Its searchable contexts will be removed. Cards already saved from it keep their copied attribution.',
          confirmLabel: 'Delete source',
        },
        ariaLabel: 'Confirm source deletion',
        autoFocus: 'button',
        restoreFocus: true,
      })
      .closed.pipe(filter(Boolean))
      .subscribe(() => this.state.delete());
  }

  kindLabel(kind: SourceDetailsModel['kind']): string {
    return (
      {
        PASTED_TEXT: 'Pasted text',
        TXT: 'TXT document',
        PDF: 'PDF document',
        EPUB: 'EPUB document',
        YOUTUBE_VIDEO: 'YouTube captions',
      } as const
    )[kind];
  }

  statusLabel(status: SourceDetailsModel['status']): string {
    return status.toLowerCase().replace('_', ' ');
  }

  statusTone(status: SourceDetailsModel['status']): StatusTone {
    if (status === 'READY') return 'success';
    if (status === 'FAILED') return 'danger';
    if (status === 'DELETING') return 'warning';
    return 'info';
  }

  failureGuidance(code: string | undefined): string {
    return (
      {
        PDF_NEEDS_OCR: 'This PDF has no readable text layer. Run OCR and upload it again.',
        ENCRYPTED_DOCUMENT: 'Remove document encryption and upload it again.',
        INVALID_TEXT_ENCODING: 'Save the TXT file as UTF-8 and upload it again.',
        NO_VIDEO_CAPTIONS: 'This video has no supported captions for your source language.',
        DOCUMENT_HAS_NO_TEXT: 'The document did not contain readable text.',
      }[code ?? ''] ?? 'The source could not be processed. Review it and try a new source.'
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
}
