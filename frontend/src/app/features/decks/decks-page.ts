import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { InputText } from 'primeng/inputtext';

import { Deck, DeckApi, DeckExport } from '@api/api-adapters/deck-api';
import { ActionMenu, ActionMenuItem } from '@app/shared/components/action-menu/action-menu';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { FormField } from '@app/shared/components/form-field/form-field';
import { UiState } from '@app/shared/components/ui-state/ui-state';
import { Pagination } from '@app/shared/components/pagination/pagination';
import { SearchInput } from '@app/shared/components/search-input/search-input';
import { StatusBadge, StatusTone } from '@app/shared/components/status-badge/status-badge';
import { ToastService } from '@app/shared/components/toast/toast.service';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { TranslationService } from '@core/i18n/translation.service';
import { ActivityMonitor } from '@core/activity/activity-monitor';
import { AppIcon } from '@app/shared/components/icon/app-icon';

type ThemeColor = NonNullable<Deck['themeColor']>;
type TranslationKey = Parameters<TranslationService['translate']>[0];

@Component({
  selector: 'app-decks-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ActionMenu,
    AppIcon,
    ButtonDirective,
    FormField,
    InputText,
    Pagination,
    SearchInput,
    StatusBadge,
    UiState,
    TranslatePipe,
  ],
  templateUrl: './decks-page.html',
  styleUrl: './decks-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DecksPage {
  readonly api = inject(DeckApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly translations = inject(TranslationService);
  private readonly activityMonitor = inject(ActivityMonitor);
  private readonly toasts = inject(ToastService);

  readonly decks = signal<Deck[]>([]);
  readonly exports = signal<DeckExport[]>([]);
  readonly deckPage = signal(0);
  readonly deckTotalPages = signal(0);
  readonly exportPage = signal(0);
  readonly exportTotalPages = signal(0);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly search = new FormControl('', { nonNullable: true });
  readonly editorOpen = signal(false);
  readonly selected = signal<Deck | null>(null);
  private readonly deckNameInput = viewChild<ElementRef<HTMLInputElement>>('deckNameInput');
  readonly colors: readonly ThemeColor[] = ['GREEN', 'ORANGE', 'BLUE', 'TEAL', 'PURPLE'];
  readonly hasPendingExports = computed(() =>
    this.exports().some((item) => item.state === 'QUEUED' || item.state === 'RUNNING'),
  );
  readonly deckActions: readonly ActionMenuItem[] = [
    { id: 'edit', label: '', labelKey: 'decks.renameOrColor' },
    { id: 'delete', label: '', labelKey: 'common.delete', danger: true },
  ];
  readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [
        (control) => Validators.required(control),
        (control) => Validators.maxLength(255)(control),
      ],
    }),
    themeColor: new FormControl<ThemeColor>('GREEN', {
      nonNullable: true,
      validators: [(control) => Validators.required(control)],
    }),
  });

  constructor() {
    effect(() => {
      if (this.editorOpen()) {
        queueMicrotask(() => this.deckNameInput()?.nativeElement.focus());
      }
    });
    this.load();
    // A refresh reconstructs status from the server; polling only runs while this page lives.
    interval(4_000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.hasPendingExports()) this.loadExports();
      });
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.list(this.deckPage(), 20, this.search.value.trim() || undefined).subscribe({
      next: (page) => {
        this.decks.set(page.items ?? []);
        this.deckPage.set(page.page ?? 0);
        this.deckTotalPages.set(page.totalPages ?? 0);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.translations.translate('decks.loadError'));
        this.loading.set(false);
      },
    });
    this.loadExports();
  }

  loadExports(): void {
    this.api.exports(this.exportPage()).subscribe({
      next: (page) => {
        this.exports.set(page.items ?? []);
        this.exportPage.set(page.page ?? 0);
        this.exportTotalPages.set(page.totalPages ?? 0);
      },
      error: () => this.error.set(this.translations.translate('decks.exportHistoryError')),
    });
  }

  goToDeckPage(page: number): void {
    this.deckPage.set(page - 1);
    this.load();
  }

  applySearch(): void {
    this.deckPage.set(0);
    this.load();
  }

  goToExportPage(page: number): void {
    this.exportPage.set(page - 1);
    this.loadExports();
  }

  newDeck(): void {
    this.selected.set(null);
    this.form.reset({ name: '', themeColor: 'GREEN' });
    this.editorOpen.set(true);
  }

  edit(deck: Deck): void {
    this.selected.set(deck);
    this.form.setValue({
      name: deck.name ?? '',
      themeColor: deck.themeColor ?? 'GREEN',
    });
    this.editorOpen.set(true);
  }

  closeEditor(): void {
    this.editorOpen.set(false);
    this.selected.set(null);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const body = this.form.getRawValue();
    const current = this.selected();
    const result =
      current?.reference != null ? this.api.update(current.reference, body) : this.api.create(body);
    result.subscribe({
      next: () => {
        this.closeEditor();
        this.load();
        this.toasts.show(
          this.translations.translate('decks.saveSuccessTitle'),
          this.translations.translate(current ? 'decks.deckUpdated' : 'decks.deckCreated'),
          'success',
        );
      },
      error: () => this.error.set(this.translations.translate('decks.saveError')),
    });
  }

  requestExport(deck: Deck): void {
    if (!deck.reference || !deck.cardCount) {
      this.error.set(this.translations.translate('decks.addCardBeforeExport'));
      return;
    }
    this.api.export(deck.reference).subscribe({
      next: () => {
        this.exportPage.set(0);
        this.loadExports();
        this.activityMonitor.refreshBaseline();
        this.toasts.show(
          this.translations.translate('decks.exportQueuedTitle'),
          this.translations.translate('decks.exportQueued'),
          'info',
        );
        const history = document.querySelector('#export-history');
        if (history && 'scrollIntoView' in history) {
          history.scrollIntoView({ behavior: 'smooth' });
        }
      },
      error: () => this.error.set(this.translations.translate('decks.queueError')),
    });
  }

  delete(deck: Deck): void {
    if (
      !deck.reference ||
      !confirm(this.translations.translate('decks.deleteConfirm', { name: deck.name ?? '' }))
    )
      return;
    this.api.delete(deck.reference).subscribe({
      next: () => {
        this.load();
        this.toasts.show(
          this.translations.translate('decks.deleteSuccessTitle'),
          this.translations.translate('decks.deckDeleted'),
          'success',
        );
      },
      error: () => this.error.set(this.translations.translate('decks.deleteError')),
    });
  }

  action(deck: Deck, action: string): void {
    if (action === 'edit') this.edit(deck);
    if (action === 'delete') this.delete(deck);
  }

  hasSearch(): boolean {
    return this.search.value.trim().length > 0;
  }

  emptyTitleKey(): TranslationKey {
    return this.hasSearch() ? 'decks.noResults' : 'decks.emptyTitle';
  }

  emptyDescriptionKey(): TranslationKey {
    return this.hasSearch() ? 'decks.noResultsDescription' : 'decks.emptyDescription';
  }

  clearSearch(): void {
    this.search.setValue('');
    this.applySearch();
  }

  nameError(): string {
    const control = this.form.controls.name;
    if (!control.touched && !control.dirty) return '';
    if (control.hasError('required')) return this.translations.translate('decks.nameRequired');
    if (control.hasError('maxlength')) return this.translations.translate('decks.nameTooLong');
    return '';
  }

  initials(name?: string): string {
    const words = (name ?? 'Deck').trim().split(/\s+/u);
    return words
      .slice(0, 2)
      .map((word) => word[0]?.toLocaleUpperCase() ?? '')
      .join('');
  }

  statusLabel(state?: DeckExport['state']): string {
    if (!state) return this.translations.translate('decks.unknown');
    const keys = {
      QUEUED: 'decks.queued',
      RUNNING: 'decks.running',
      READY: 'decks.ready',
      FAILED: 'decks.failed',
      EXPIRED: 'decks.expired',
    } as const;
    return this.translations.translate(keys[state]);
  }

  statusTone(state?: DeckExport['state']): StatusTone {
    if (state === 'READY') return 'success';
    if (state === 'QUEUED' || state === 'RUNNING') return 'warning';
    if (state === 'FAILED') return 'danger';
    return 'neutral';
  }

  formatDate(value?: string): string {
    if (!value) return '—';
    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? '—'
      : new Intl.DateTimeFormat(this.translations.language(), {
          month: 'short',
          day: 'numeric',
        }).format(date);
  }
}
