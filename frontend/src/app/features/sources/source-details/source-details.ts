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
import type { TranslationKey } from '@core/i18n/translation.service';
import { AppIcon } from '@app/shared/components/icon/app-icon';
import { formatBytes } from '@app/shared/utils/format-bytes';

import { SourceDetailsState } from './source-details-state';

const FAILURE_GUIDANCE_KEYS = {
  PDF_NEEDS_OCR: 'sourceDetails.failurePdfOcr',
  ENCRYPTED_DOCUMENT: 'sourceDetails.failureEncrypted',
  INVALID_TEXT_ENCODING: 'sourceDetails.failureEncoding',
  NO_VIDEO_CAPTIONS: 'sourceDetails.failureCaptions',
  DOCUMENT_HAS_NO_TEXT: 'sourceDetails.failureNoText',
} satisfies Record<string, TranslationKey>;

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
        TIMED_TRANSCRIPT: this.translations.translate('sources.timedTranscript'),
      } satisfies Record<SourceDetailsModel['kind'], string>
    )[kind];
  }

  statusLabel(status: SourceDetailsModel['status']): string {
    const keys = {
      READY: 'sourceDetails.statusReady',
      RECEIVED: 'sourceDetails.statusReceived',
      FETCHING: 'sourceDetails.statusFetching',
      STORED: 'sourceDetails.statusStored',
      PARSING: 'sources.parsing',
      NORMALIZING: 'sources.normalizing',
      EXTRACTING: 'sourceDetails.statusExtracting',
      INDEXING: 'sourceDetails.statusIndexing',
      FAILED: 'sourceDetails.statusFailed',
      DELETING: 'sourceDetails.statusDeleting',
      DELETED: 'sourceDetails.statusDeleted',
    } satisfies Record<SourceDetailsModel['status'], TranslationKey>;
    return this.translations.translate(keys[status]);
  }

  statusTone(status: SourceDetailsModel['status']): StatusTone {
    if (status === 'READY') return 'success';
    if (status === 'FAILED') return 'danger';
    if (status === 'DELETING') return 'warning';
    return 'info';
  }

  failureGuidance(code: string | undefined): string {
    return this.translations.translate(
      code && isFailureGuidanceCode(code)
        ? FAILURE_GUIDANCE_KEYS[code]
        : 'sourceDetails.failureGeneric',
    );
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

  formatBytes(bytes: number | undefined): string {
    return formatBytes(bytes);
  }

  youtubeThumbnail(videoId: string): string | null {
    return /^[A-Za-z0-9_-]{11}$/u.test(videoId)
      ? `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`
      : null;
  }

  youtubePreviewVideoId(timed: NonNullable<SourceDetailsModel['timedTranscript']>): string | null {
    if (timed.originProvider === 'YOUTUBE' && timed.originProviderId) {
      return this.youtubeThumbnail(timed.originProviderId) ? timed.originProviderId : null;
    }
    if (!timed.originUrl) return null;
    try {
      const url = new URL(timed.originUrl);
      const host = url.hostname.toLowerCase();
      const videoId =
        host === 'youtu.be'
          ? url.pathname.slice(1)
          : ['youtube.com', 'www.youtube.com', 'm.youtube.com'].includes(host)
            ? url.searchParams.get('v')
            : null;
      return videoId && this.youtubeThumbnail(videoId) ? videoId : null;
    } catch {
      return null;
    }
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

  formatDuration(milliseconds: number): string {
    const totalSeconds = Math.floor(milliseconds / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    return [hours, minutes, seconds].map((value) => value.toString().padStart(2, '0')).join(':');
  }
}

function isFailureGuidanceCode(value: string): value is keyof typeof FAILURE_GUIDANCE_KEYS {
  return Object.hasOwn(FAILURE_GUIDANCE_KEYS, value);
}
