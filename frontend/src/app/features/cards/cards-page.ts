import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { BrowserCardDto, CardApi } from '@api/api-adapters/card-api';
import { BrowserDeckOptionDto, LookupApi } from '@api/api-adapters/lookup-api';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { ActionMenu, ActionMenuItem } from '@app/shared/components/action-menu/action-menu';
import { UiState } from '@app/shared/components/ui-state/ui-state';
@Component({
  selector: 'app-cards-page',
  imports: [ReactiveFormsModule, RouterLink, ButtonDirective, ActionMenu, UiState],
  templateUrl: './cards-page.html',
  styleUrl: './cards-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CardsPage {
  private readonly api = inject(CardApi);
  private readonly lookupApi = inject(LookupApi);
  private readonly route = inject(ActivatedRoute);
  readonly cards = signal<BrowserCardDto[]>([]);
  readonly decks = signal<BrowserDeckOptionDto[]>([]);
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
    { id: 'edit', label: 'Edit card' },
    { id: 'delete', label: 'Delete card', danger: true },
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
    this.lookupApi.decks().subscribe({ next: (d) => this.decks.set(d) });
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
          this.error.set('Cards could not be loaded.');
          this.loading.set(false);
        },
      });
  }
  edit(card: BrowserCardDto): void {
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
      error: () => this.error.set('The card could not be saved. It may have changed elsewhere.'),
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
      error: () => this.error.set('The card could not be deleted.'),
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
    if (elapsedMinutes < 1) return 'Just now';
    if (elapsedMinutes < 60) return `${elapsedMinutes} min`;
    if (elapsedMinutes < 1_440) return `${Math.floor(elapsedMinutes / 60)} hr`;
    if (elapsedMinutes < 2_880) return 'Yesterday';
    return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }).format(updated);
  }
}
