import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Table } from 'primeng/table';

import { SourceListQuery, SourceSummary } from '@api/api-adapters/source-api';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { FormField } from '@app/shared/components/form-field/form-field';
import { Pagination } from '@app/shared/components/pagination/pagination';
import { StatusBadge, StatusTone } from '@app/shared/components/status-badge/status-badge';
import { UiState } from '@app/shared/components/ui-state/ui-state';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { TranslationService } from '@core/i18n/translation.service';

import { SourceListState } from './source-list-state';

@Component({
  selector: 'app-source-list',
  imports: [
    ButtonDirective,
    FormField,
    InputText,
    Pagination,
    ReactiveFormsModule,
    RouterLink,
    Select,
    StatusBadge,
    UiState,
    TranslatePipe,
    Table,
  ],
  providers: [SourceListState],
  templateUrl: './source-list.html',
  styleUrl: './source-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SourceList {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly translations = inject(TranslationService);

  readonly state = inject(SourceListState);
  readonly filters = new FormGroup({
    search: new FormControl('', { nonNullable: true }),
    kind: new FormControl('', { nonNullable: true }),
    status: new FormControl('', { nonNullable: true }),
    sort: new FormControl('CREATED_AT', { nonNullable: true }),
    direction: new FormControl('DESC', { nonNullable: true }),
  });

  readonly kindOptions = computed(() => [
    { label: this.translations.translate('sources.allTypes'), value: '' },
    { label: this.translations.translate('sources.pastedText'), value: 'PASTED_TEXT' },
    { label: this.translations.translate('sources.youtube'), value: 'YOUTUBE_VIDEO' },
    { label: 'PDF', value: 'PDF' },
    { label: 'TXT', value: 'TXT' },
    { label: 'EPUB', value: 'EPUB' },
  ]);

  readonly statusOptions = computed(() => [
    { label: this.translations.translate('sources.allStatuses'), value: '' },
    { label: this.translations.translate('sources.ready'), value: 'READY' },
    { label: this.translations.translate('sources.received'), value: 'RECEIVED' },
    { label: this.translations.translate('sources.fetching'), value: 'FETCHING' },
    { label: this.translations.translate('sources.stored'), value: 'STORED' },
    { label: this.translations.translate('sources.extracting'), value: 'EXTRACTING' },
    { label: this.translations.translate('sources.indexing'), value: 'INDEXING' },
    { label: this.translations.translate('sources.failed'), value: 'FAILED' },
    { label: this.translations.translate('sources.deleting'), value: 'DELETING' },
  ]);

  readonly sortOptions = computed(() => [
    { label: this.translations.translate('sources.created'), value: 'CREATED_AT' },
    { label: this.translations.translate('sources.updated'), value: 'UPDATED_AT' },
    { label: this.translations.translate('sources.name'), value: 'DISPLAY_NAME' },
  ]);

  readonly directionOptions = computed(() => [
    { label: this.translations.translate('sources.descending'), value: 'DESC' },
    { label: this.translations.translate('sources.ascending'), value: 'ASC' },
  ]);

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.filters.setValue(
        {
          search: params.get('search') ?? '',
          kind: params.get('kind') ?? '',
          status: params.get('status') ?? '',
          sort: params.get('sort') ?? 'CREATED_AT',
          direction: params.get('direction') ?? 'DESC',
        },
        { emitEvent: false },
      );
      this.state.load(this.queryFromRoute());
    });
  }

  applyFilters(): void {
    const value = this.filters.getRawValue();
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        page: null,
        search: value.search.trim() || null,
        kind: value.kind || null,
        status: value.status || null,
        sort: value.sort === 'CREATED_AT' ? null : value.sort,
        direction: value.direction === 'DESC' ? null : value.direction,
      },
    });
  }

  clearFilters(): void {
    void this.router.navigate([], { relativeTo: this.route });
  }

  goToPage(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page - 1 || null },
      queryParamsHandling: 'merge',
    });
  }

  kindLabel(kind: SourceSummary['kind']): string {
    return (
      {
        PASTED_TEXT: this.translations.translate('sources.pastedText'),
        TXT: 'TXT',
        PDF: 'PDF',
        EPUB: 'EPUB',
        YOUTUBE_VIDEO: this.translations.translate('sources.youtube'),
      } as const
    )[kind];
  }

  statusLabel(status: SourceSummary['status']): string {
    const keys = {
      READY: 'sources.ready',
      RECEIVED: 'sources.received',
      FETCHING: 'sources.fetching',
      STORED: 'sources.stored',
      EXTRACTING: 'sources.extracting',
      INDEXING: 'sources.indexing',
      FAILED: 'sources.failed',
      DELETING: 'sources.deleting',
      DELETED: 'sources.deleted',
    } as const;
    return this.translations.translate(keys[status]);
  }

  statusTone(status: SourceSummary['status']): StatusTone {
    if (status === 'READY') return 'success';
    if (status === 'FAILED') return 'danger';
    if (status === 'DELETING') return 'warning';
    return 'info';
  }

  formatUpdatedDate(value: string): string {
    return new Intl.DateTimeFormat(document.documentElement.lang || navigator.language, {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    }).format(new Date(value));
  }

  formatStorage(bytes: number): string {
    if (bytes < 1_000) return `${bytes} B`;
    const units = ['KB', 'MB', 'GB', 'TB'];
    let value = bytes / 1_000;
    let unitIndex = 0;
    while (value >= 1_000 && unitIndex < units.length - 1) {
      value /= 1_000;
      unitIndex += 1;
    }
    const formatted = new Intl.NumberFormat(document.documentElement.lang || navigator.language, {
      maximumFractionDigits: value < 10 ? 1 : 0,
    }).format(value);
    return `${formatted} ${units[unitIndex]}`;
  }

  private queryFromRoute(): SourceListQuery {
    const params = this.route.snapshot.queryParamMap;
    return {
      page: Math.max(0, Number(params.get('page') ?? 0) || 0),
      pageSize: 20,
      search: params.get('search') ?? undefined,
      kind: (params.get('kind') as SourceListQuery['kind']) ?? undefined,
      status: (params.get('status') as SourceListQuery['status']) ?? undefined,
      sort: (params.get('sort') as SourceListQuery['sort']) ?? 'CREATED_AT',
      direction: (params.get('direction') as SourceListQuery['direction']) ?? 'DESC',
    };
  }
}
