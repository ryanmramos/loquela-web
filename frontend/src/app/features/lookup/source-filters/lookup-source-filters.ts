import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { SourceSummary } from '@api/api-adapters/source-api';

interface FilterToggle {
  readonly value: string;
  readonly checked: boolean;
}

@Component({
  selector: 'app-lookup-source-filters',
  templateUrl: './lookup-source-filters.html',
  styleUrl: './lookup-source-filters.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LookupSourceFilters {
  readonly sources = input.required<SourceSummary[]>();
  readonly selectedKinds = input.required<string[]>();
  readonly selectedSources = input.required<string[]>();
  readonly kindToggled = output<FilterToggle>();
  readonly sourceToggled = output<FilterToggle>();
  readonly activeCount = computed(
    () => this.selectedKinds().length + this.selectedSources().length,
  );
  readonly kinds = [
    { value: 'YOUTUBE_VIDEO', label: 'Video' },
    { value: 'PASTED_TEXT', label: 'Pasted text' },
    { value: 'TXT', label: 'TXT' },
    { value: 'PDF', label: 'PDF' },
    { value: 'EPUB', label: 'EPUB' },
  ] as const;

  toggleKind(value: string, checked: boolean): void {
    this.kindToggled.emit({ value, checked });
  }

  toggleSource(value: string | undefined, checked: boolean): void {
    if (value) this.sourceToggled.emit({ value, checked });
  }

  sourceKindLabel(kind: string | undefined): string {
    return kind === 'YOUTUBE_VIDEO' ? 'Video' : (kind ?? 'Source').replaceAll('_', ' ');
  }

  sourceIcon(kind: string | undefined): string {
    if (kind === 'YOUTUBE_VIDEO') return '▶';
    if (kind === 'PDF') return 'PDF';
    if (kind === 'EPUB') return 'EPUB';
    return 'T';
  }
}
