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

type ThemeColor = NonNullable<Deck['themeColor']>;

@Component({
  selector: 'app-decks-page',
  imports: [ReactiveFormsModule, RouterLink, ActionMenu, ButtonDirective, UiState],
  templateUrl: './decks-page.html',
  styleUrl: './decks-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DecksPage {
  readonly api = inject(DeckApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly decks = signal<Deck[]>([]);
  readonly exports = signal<DeckExport[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly editorOpen = signal(false);
  readonly selected = signal<Deck | null>(null);
  readonly colors: readonly ThemeColor[] = ['GREEN', 'ORANGE', 'BLUE', 'TEAL', 'PURPLE'];
  readonly hasPendingExports = computed(() =>
    this.exports().some((item) => item.state === 'QUEUED' || item.state === 'RUNNING'),
  );
  readonly deckActions: readonly ActionMenuItem[] = [
    { id: 'edit', label: 'Rename or change color' },
    { id: 'export', label: 'Export for Anki' },
    { id: 'delete', label: 'Delete deck', danger: true },
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
    this.api.list().subscribe({
      next: (decks) => {
        this.decks.set(decks);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Decks could not be loaded.');
        this.loading.set(false);
      },
    });
    this.loadExports();
  }

  loadExports(): void {
    this.api.exports().subscribe({
      next: (exports) => this.exports.set(exports),
      error: () => this.error.set('Export history could not be loaded.'),
    });
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
      error: () => this.error.set('The deck name is already used or could not be saved.'),
    });
  }

  requestExport(deck: Deck): void {
    if (!deck.reference || !deck.cardCount) {
      this.error.set('Add at least one card before exporting this deck.');
      return;
    }
    this.api.export(deck.reference).subscribe({
      next: (item) => {
        this.exports.update((items) => [item, ...items]);
        const history = document.querySelector('#export-history');
        if (history && 'scrollIntoView' in history) {
          history.scrollIntoView({ behavior: 'smooth' });
        }
      },
      error: () => this.error.set('The export could not be queued. Review the deck and try again.'),
    });
  }

  delete(deck: Deck): void {
    if (!deck.reference || !confirm(`Delete “${deck.name}”? Only empty decks can be deleted.`))
      return;
    this.api.delete(deck.reference).subscribe({
      next: () => this.load(),
      error: () =>
        this.error.set(
          'Move or delete this deck’s cards and finish active exports before deleting it.',
        ),
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
    return state ? state[0] + state.slice(1).toLocaleLowerCase() : 'Unknown';
  }

  formatDate(value?: string): string {
    if (!value) return '—';
    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? '—'
      : new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }).format(date);
  }
}
