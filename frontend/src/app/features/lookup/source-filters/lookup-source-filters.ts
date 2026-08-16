import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';

import { SourceSummary } from '@api/api-adapters/source-api';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { TranslationService } from '@core/i18n/translation.service';
import { AppIcon } from '@app/shared/components/icon/app-icon';

interface FilterToggle {
  readonly value: string;
  readonly checked: boolean;
}

@Component({
  selector: 'app-lookup-source-filters',
  imports: [AppIcon, TranslatePipe],
  templateUrl: './lookup-source-filters.html',
  styleUrl: './lookup-source-filters.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LookupSourceFilters {
  private readonly translations = inject(TranslationService);
  readonly sources = input.required<SourceSummary[]>();
  readonly selectedKinds = input.required<string[]>();
  readonly selectedSources = input.required<string[]>();
  readonly kindToggled = output<FilterToggle>();
  readonly sourceToggled = output<FilterToggle>();
  readonly activeCount = computed(
    () => this.selectedKinds().length + this.selectedSources().length,
  );
  readonly kinds = [
    { value: 'TIMED_TRANSCRIPT', label: 'lookup.timedTranscriptType' },
    { value: 'PASTED_TEXT', label: 'lookup.pastedType' },
    { value: 'TXT', label: 'lookup.txtType' },
    { value: 'PDF', label: 'lookup.pdfType' },
    { value: 'EPUB', label: 'lookup.epubType' },
  ] as const;

  toggleKind(value: string, checked: boolean): void {
    this.kindToggled.emit({ value, checked });
  }

  toggleSource(value: string | undefined, checked: boolean): void {
    if (value) this.sourceToggled.emit({ value, checked });
  }

  sourceKindLabel(kind: string | undefined): string {
    const keys = {
      YOUTUBE_VIDEO: 'lookup.videoType',
      TIMED_TRANSCRIPT: 'lookup.timedTranscriptType',
      PASTED_TEXT: 'lookup.pastedType',
      TXT: 'lookup.txtSourceType',
      PDF: 'lookup.pdfSourceType',
      EPUB: 'lookup.epubSourceType',
    } as const;
    return kind && kind in keys
      ? this.translations.translate(keys[kind as keyof typeof keys])
      : this.translations.translate('lookup.genericSource');
  }

  sourceIcon(kind: string | undefined): string {
    if (kind === 'YOUTUBE_VIDEO' || kind === 'TIMED_TRANSCRIPT') return '▶';
    if (kind === 'PDF') return 'PDF';
    if (kind === 'EPUB') return 'EPUB';
    return 'T';
  }
}
