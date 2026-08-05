import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';

import { Deck, DeckApi, DeckExport } from '@api/api-adapters/deck-api';
import { ActionMenu, ActionMenuItem } from '@app/shared/components/action-menu/action-menu';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { UiState } from '@app/shared/components/ui-state/ui-state';
import { Pagination } from '@app/shared/components/pagination/pagination';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { TranslationService } from '@core/i18n/translation.service';
import { ActivityMonitor } from '@core/activity/activity-monitor';

type ThemeColor = NonNullable<Deck['themeColor']>;

@Component({
  selector: 'app-decks-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ActionMenu,
    ButtonDirective,
    Pagination,
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
  readonly colors: readonly ThemeColor[] = ['GREEN', 'ORANGE', 'BLUE', 'TEAL', 'PURPLE'];
  readonly hasPendingExports = computed(() =>
    this.exports().some((item) => item.state === 'QUEUED' || item.state === 'RUNNING'),
  );
  readonly deckActions: readonly ActionMenuItem[] = [
    { id: 'edit', label: '', labelKey: 'decks.renameOrColor' },
    { id: 'export', label: '', labelKey: 'decks.exportForAnki' },
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
    if (this.form.invalid) return;
    const body = this.form.getRawValue();
    const current = this.selected();
    const result =
      current?.reference != null ? this.api.update(current.reference, body) : this.api.create(body);
    result.subscribe({
      next: () => {
        this.closeEditor();
        this.load();
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
      next: () => this.load(),
      error: () => this.error.set(this.translations.translate('decks.deleteError')),
    });
  }

  action(deck: Deck, action: string): void {
    if (action === 'edit') this.edit(deck);
    if (action === 'export') this.requestExport(deck);
    if (action === 'delete') this.delete(deck);
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
