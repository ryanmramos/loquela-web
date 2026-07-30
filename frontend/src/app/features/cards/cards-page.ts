import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { BrowserCardDto, CardApi } from '@api/api-adapters/card-api';
import { BrowserDeckOptionDto, LookupApi } from '@api/api-adapters/lookup-api';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { UiState } from '@app/shared/components/ui-state/ui-state';
@Component({
  selector: 'app-cards-page',
  imports: [ReactiveFormsModule, ButtonDirective, UiState],
  templateUrl: './cards-page.html',
  styleUrl: './cards-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CardsPage {
  private readonly api = inject(CardApi);
  private readonly lookupApi = inject(LookupApi);
  readonly cards = signal<BrowserCardDto[]>([]);
  readonly decks = signal<BrowserDeckOptionDto[]>([]);
  readonly selected = signal<BrowserCardDto | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly filter = new FormControl('', { nonNullable: true });
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
    this.load();
    this.lookupApi.decks().subscribe({ next: (d) => this.decks.set(d) });
  }
  load(): void {
    this.loading.set(true);
    this.api.list(this.filter.value.trim() || undefined).subscribe({
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
        this.newCard();
        this.load();
      },
      error: () => this.error.set('The card could not be saved. It may have changed elsewhere.'),
    });
  }
  delete(card: BrowserCardDto): void {
    if (!card.reference || !confirm(`Permanently delete “${card.term}”?`)) return;
    this.api.delete(card.reference).subscribe({
      next: () => {
        if (this.selected()?.reference === card.reference) this.newCard();
        this.load();
      },
      error: () => this.error.set('The card could not be deleted.'),
    });
  }
}
