import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { BrowserCardDto, CardApi } from '@api/api-adapters/card-api';
import { BrowserDeckOptionDto, LookupApi } from '@api/api-adapters/lookup-api';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { ActionMenu, ActionMenuItem } from '@app/shared/components/action-menu/action-menu';
import { UiState } from '@app/shared/components/ui-state/ui-state';
import {
  SearchableSelect,
  SearchableSelectOption,
} from '@app/shared/components/searchable-select/searchable-select';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { TranslationService } from '@core/i18n/translation.service';
@Component({
  selector: 'app-cards-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonDirective,
    ActionMenu,
    SearchableSelect,
    UiState,
    TranslatePipe,
  ],
  templateUrl: './cards-page.html',
  styleUrl: './cards-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CardsPage {
  private readonly api = inject(CardApi);
  private readonly lookupApi = inject(LookupApi);
  private readonly route = inject(ActivatedRoute);
  private readonly translations = inject(TranslationService);
  readonly cards = signal<BrowserCardDto[]>([]);
  readonly decks = signal<BrowserDeckOptionDto[]>([]);
  readonly pinnedDeck = signal<BrowserDeckOptionDto | null>(null);
  readonly deckOptions = computed<SearchableSelectOption[]>(() => {
    const options = this.decks().map((deck) => ({
      value: deck.reference ?? '',
      label: deck.name ?? '',
    }));
    const pinned = this.pinnedDeck();
    if (pinned?.reference && !options.some((option) => option.value === pinned.reference)) {
      options.unshift({ value: pinned.reference, label: pinned.name ?? '' });
    }
    return options.filter((option) => option.value && option.label);
  });
  readonly deckFilterOptions = computed<SearchableSelectOption[]>(() => [
    { value: '', label: this.translations.translate('cards.allDecks') },
    ...this.deckOptions(),
  ]);
  readonly selected = signal<BrowserCardDto | null>(null);
  readonly editorOpen = signal(false);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly selectedReferences = signal<ReadonlySet<string>>(new Set());
  readonly filter = new FormControl('', { nonNullable: true });
  readonly deckFilter = new FormControl('', { nonNullable: true });
  readonly noteTypeFilter = new FormControl('', { nonNullable: true });
  readonly activeNoteType = signal('');
  readonly displayedCards = computed(() => {
    const noteType = this.activeNoteType();
    return noteType ? this.cards().filter((card) => card.noteType === noteType) : this.cards();
  });
  readonly selectedCount = computed(() => this.selectedReferences().size);
  readonly allVisibleSelected = computed(() => {
    const visible = this.displayedCards().filter((card) => card.reference);
    return (
      visible.length > 0 &&
      visible.every((card) => this.selectedReferences().has(card.reference ?? ''))
    );
  });
  readonly cardActions: readonly ActionMenuItem[] = [
    { id: 'edit', label: '', labelKey: 'cards.edit' },
    { id: 'delete', label: '', labelKey: 'cards.delete', danger: true },
  ];
  readonly form = new FormGroup({
    deckReference: new FormControl('', {
      nonNullable: true,
      validators: (control) => Validators.required(control),
    }),
    term: new FormControl('', {
      nonNullable: true,
      validators: (control) => Validators.required(control),
    }),
    definition: new FormControl('', {
      nonNullable: true,
      validators: (control) => Validators.required(control),
    }),
    exampleText: new FormControl('', { nonNullable: true }),
    version: new FormControl<number | null>(null),
  });
  constructor() {
    this.deckFilter.setValue(this.route.snapshot.queryParamMap.get('deckReference') ?? '');
    this.load();
    this.searchDecks('');
    this.deckFilter.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.load());
  }
  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api
      .list(this.filter.value.trim() || undefined, this.deckFilter.value || undefined)
      .subscribe({
        next: (p) => {
          this.cards.set(p.items ?? []);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(this.translations.translate('cards.loadError'));
          this.loading.set(false);
        },
      });
  }
  edit(card: BrowserCardDto): void {
    this.pinnedDeck.set({ reference: card.deckReference, name: card.deckName });
    this.selected.set(card);
    this.form.setValue({
      deckReference: card.deckReference ?? '',
      term: card.term ?? '',
      definition: card.definition ?? '',
      exampleText: card.exampleText ?? '',
      version: card.version ?? null,
    });
    this.editorOpen.set(true);
  }
  newCard(): void {
    this.selected.set(null);
    this.pinnedDeck.set(null);
    this.form.reset({
      deckReference: this.decks()[0]?.reference ?? '',
      term: '',
      definition: '',
      exampleText: '',
      version: null,
    });
    this.editorOpen.set(true);
  }
  closeEditor(): void {
    this.editorOpen.set(false);
    this.selected.set(null);
    this.pinnedDeck.set(null);
  }

  searchDecks(search: string): void {
    this.lookupApi.decks(search || undefined).subscribe({
      next: (page) => this.decks.set(page.items ?? []),
      error: () => this.error.set(this.translations.translate('cards.loadError')),
    });
  }
  applyNoteTypeFilter(): void {
    this.activeNoteType.set(this.noteTypeFilter.value);
  }
  save(): void {
    if (this.form.invalid) return;
    const body = this.form.getRawValue();
    const current = this.selected();
    const request = { ...body, version: current ? (body.version ?? undefined) : undefined };
    const result = current?.reference
      ? this.api.update(current.reference, request)
      : this.api.create(request);
    result.subscribe({
      next: () => {
        this.closeEditor();
        this.load();
      },
      error: () => this.error.set(this.translations.translate('cards.saveError')),
    });
  }
  delete(card: BrowserCardDto): void {
    if (!card.reference || !confirm(`Permanently delete “${card.term}”?`)) return;
    this.api.delete(card.reference).subscribe({
      next: () => {
        if (this.selected()?.reference === card.reference) this.closeEditor();
        this.selectedReferences.update((references) => {
          const next = new Set(references);
          next.delete(card.reference ?? '');
          return next;
        });
        this.load();
      },
      error: () => this.error.set(this.translations.translate('cards.deleteError')),
    });
  }
  cardAction(card: BrowserCardDto, action: string): void {
    if (action === 'edit') this.edit(card);
    if (action === 'delete') this.delete(card);
  }
  isSelected(card: BrowserCardDto): boolean {
    return !!card.reference && this.selectedReferences().has(card.reference);
  }
  toggleSelection(card: BrowserCardDto): void {
    if (!card.reference) return;
    this.selectedReferences.update((references) => {
      const next = new Set(references);
      if (next.has(card.reference!)) next.delete(card.reference!);
      else next.add(card.reference!);
      return next;
    });
  }
  toggleAllVisible(): void {
    const references = this.displayedCards()
      .map((card) => card.reference)
      .filter((reference): reference is string => !!reference);
    this.selectedReferences.update((selected) => {
      const next = new Set(selected);
      if (this.allVisibleSelected()) references.forEach((reference) => next.delete(reference));
      else references.forEach((reference) => next.add(reference));
      return next;
    });
  }
  formatUpdatedAt(value?: string): string {
    if (!value) return '—';
    const updated = new Date(value);
    if (Number.isNaN(updated.getTime())) return '—';
    const elapsedMinutes = Math.floor((Date.now() - updated.getTime()) / 60_000);
    if (elapsedMinutes < 1) return this.translations.translate('cards.justNow');
    if (elapsedMinutes < 60) return `${elapsedMinutes} min`;
    if (elapsedMinutes < 1_440) return `${Math.floor(elapsedMinutes / 60)} hr`;
    if (elapsedMinutes < 2_880) return this.translations.translate('cards.yesterday');
    return new Intl.DateTimeFormat(this.translations.language(), {
      month: 'short',
      day: 'numeric',
    }).format(updated);
  }
}
