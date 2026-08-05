import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  catchError,
  distinctUntilChanged,
  EMPTY,
  map,
  switchMap,
  takeWhile,
  tap,
  timer,
} from 'rxjs';
import { BrowserDeckOptionDto, BrowserLookupDto, LookupApi } from '@api/api-adapters/lookup-api';
import { CardApi } from '@api/api-adapters/card-api';
import { SourceApi, SourceSummary } from '@api/api-adapters/source-api';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { UiState } from '@app/shared/components/ui-state/ui-state';
import { ToastService } from '@app/shared/components/toast/toast.service';
import {
  SearchableSelect,
  SearchableSelectOption,
} from '@app/shared/components/searchable-select/searchable-select';
import { SessionFacade } from '@core/auth/session-facade';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { TranslationService } from '@core/i18n/translation.service';
import { ActivityMonitor } from '@core/activity/activity-monitor';
import { LookupSourceFilters } from './source-filters/lookup-source-filters';

interface HighlightSegment {
  readonly text: string;
  readonly highlighted: boolean;
}

@Component({
  selector: 'app-lookup-page',
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    LookupSourceFilters,
    RouterLink,
    SearchableSelect,
    UiState,
    TranslatePipe,
  ],
  templateUrl: './lookup-page.html',
  styleUrl: './lookup-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LookupPage {
  private readonly api = inject(LookupApi);
  private readonly cardApi = inject(CardApi);
  private readonly sourceApi = inject(SourceApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly toasts = inject(ToastService);
  private readonly sessionFacade = inject(SessionFacade);
  private readonly translations = inject(TranslationService);
  private readonly activityMonitor = inject(ActivityMonitor);
  readonly session = this.sessionFacade.view;
  readonly lookup = signal<BrowserLookupDto | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly decks = signal<BrowserDeckOptionDto[]>([]);
  readonly deckOptions = computed<SearchableSelectOption[]>(() =>
    this.decks()
      .map((deck) => ({ value: deck.reference ?? '', label: deck.name ?? '' }))
      .filter((option) => option.value && option.label),
  );
  readonly sources = signal<SourceSummary[]>([]);
  readonly selectedContext = signal<number | 'definition' | null>(null);
  readonly saving = signal(false);
  readonly form = new FormGroup({
    term: new FormControl('', {
      nonNullable: true,
      validators: [
        (control) => Validators.required(control),
        (control) => Validators.maxLength(256)(control),
      ],
    }),
    sourceKinds: new FormControl<string[]>([], { nonNullable: true }),
    sourceReferences: new FormControl<string[]>([], { nonNullable: true }),
  });
  readonly saveForm = new FormGroup({
    deckReference: new FormControl('', {
      nonNullable: true,
      validators: (control) => Validators.required(control),
    }),
  });
  constructor() {
    this.route.queryParamMap
      .pipe(
        tap((params) => {
          const query = params.get('query');
          if (query && !params.has('lookup')) this.form.controls.term.setValue(query);
        }),
        map((params) => params.get('lookup')),
        distinctUntilChanged(),
        switchMap((reference) => {
          if (!reference) {
            this.lookup.set(null);
            return EMPTY;
          }
          return this.poll(reference);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
    this.sourceApi
      .list({ page: 0, pageSize: 50, status: 'READY', sort: 'DISPLAY_NAME', direction: 'ASC' })
      .subscribe({
        next: (page) => this.sources.set(page.items),
        error: () => this.error.set(this.translations.translate('lookup.sourcesLoadError')),
      });
  }
  submit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set('');
    const v = this.form.getRawValue();
    this.api.create(v.term.trim(), v.sourceReferences, v.sourceKinds).subscribe({
      next: (a) => {
        this.activityMonitor.refreshBaseline();
        void this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { lookup: a.reference },
        });
      },
      error: () => {
        this.loading.set(false);
        this.error.set(this.translations.translate('lookup.startError'));
      },
    });
  }
  chooseContext(number: number): void {
    this.selectedContext.set(number);
    this.loadDecks();
  }
  chooseDefinition(): void {
    this.selectedContext.set('definition');
    this.loadDecks();
  }
  private loadDecks(): void {
    this.api.decks().subscribe({
      next: (page) => {
        const decks = page.items ?? [];
        this.decks.set(decks);
        if (decks[0]?.reference) this.saveForm.controls.deckReference.setValue(decks[0].reference);
      },
      error: () => this.error.set(this.translations.translate('lookup.decksLoadError')),
    });
  }
  save(): void {
    const lookup = this.lookup(),
      selection = this.selectedContext();
    if (!lookup || selection === null || this.saveForm.invalid) return;
    this.saving.set(true);
    if (selection === 'definition') {
      const definition = (lookup.senses ?? [])
        .map((sense) => `${sense.partOfSpeech}: ${sense.definition}`)
        .join('\n');
      if (!lookup.term || !definition) {
        this.saving.set(false);
        this.error.set(this.translations.translate('lookup.definitionSaveError'));
        return;
      }
      this.cardApi
        .create({
          deckReference: this.saveForm.getRawValue().deckReference,
          term: lookup.lemma ?? lookup.term,
          definition,
        })
        .subscribe({
          next: () => {
            this.saving.set(false);
            this.selectedContext.set(null);
            this.toasts.show(
              this.translations.translate('lookup.cardSaved'),
              this.translations.translate('lookup.definitionSaved'),
              'success',
            );
          },
          error: () => {
            this.saving.set(false);
            this.error.set(this.translations.translate('lookup.definitionSaveError'));
          },
        });
      return;
    }
    if (!lookup.reference) {
      this.saving.set(false);
      return;
    }
    this.api
      .save(lookup.reference, selection, this.saveForm.getRawValue().deckReference)
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.selectedContext.set(null);
          this.toasts.show(
            this.translations.translate('lookup.cardSaved'),
            this.translations.translate('lookup.contextCardSaved'),
            'success',
          );
        },
        error: () => {
          this.saving.set(false);
          this.error.set(this.translations.translate('lookup.resultSaveError'));
        },
      });
  }
  closeSave(): void {
    this.selectedContext.set(null);
  }
  chooseDeck(reference: string): void {
    this.saveForm.controls.deckReference.setValue(reference);
  }

  searchDecks(search: string): void {
    this.api.decks(search || undefined).subscribe({
      next: (page) => this.decks.set(page.items ?? []),
      error: () => this.error.set(this.translations.translate('lookup.decksLoadError')),
    });
  }
  toggleKind(kind: string, checked: boolean): void {
    const current = this.form.controls.sourceKinds.value;
    this.form.controls.sourceKinds.setValue(
      checked ? [...current, kind] : current.filter((value) => value !== kind),
    );
  }
  toggleSource(reference: string, checked: boolean): void {
    if (!reference) return;
    const current = this.form.controls.sourceReferences.value;
    this.form.controls.sourceReferences.setValue(
      checked ? [...current, reference] : current.filter((value) => value !== reference),
    );
  }
  sourceKindLabel(kind: string | undefined): string {
    const keys = {
      YOUTUBE_VIDEO: 'lookup.videoType',
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
    if (kind === 'YOUTUBE_VIDEO') return '▶';
    if (kind === 'PDF') return 'PDF';
    if (kind === 'EPUB') return 'EPUB';
    return 'T';
  }
  languageName(tag: string | undefined): string {
    if (!tag) return '—';
    try {
      return (
        new Intl.DisplayNames([document.documentElement.lang || navigator.language], {
          type: 'language',
        }).of(tag) ?? tag
      );
    } catch {
      return tag;
    }
  }
  sourceLocation(context: NonNullable<BrowserLookupDto['contexts']>[number]): string {
    if (context.youtubeUrl && context.startMs !== undefined)
      return `${this.translations.translate('lookup.video')} · ${this.formatDuration(context.startMs)}`;
    if (context.pageNumber !== undefined)
      return `${this.sourceKindLabel(context.sourceKind)} · ${this.translations.translate('lookup.page')} ${context.pageNumber}`;
    return `${this.sourceKindLabel(context.sourceKind)} · ${this.translations.translate('lookup.context')} ${context.ordinal ?? ''}`;
  }
  youtubeThumbnail(context: NonNullable<BrowserLookupDto['contexts']>[number]): string | null {
    if (!context.youtubeUrl) return null;
    try {
      const url = new URL(context.youtubeUrl);
      const videoId = url.hostname.endsWith('youtube.com') ? url.searchParams.get('v') : null;
      return videoId && /^[A-Za-z0-9_-]{6,64}$/u.test(videoId)
        ? `https://i.ytimg.com/vi/${videoId}/mqdefault.jpg`
        : null;
    } catch {
      return null;
    }
  }
  highlightedContext(text: string, term: string): HighlightSegment[] {
    const needle = term.trim();
    if (!needle) return [{ text, highlighted: false }];
    const pattern = new RegExp(needle.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&'), 'giu');
    const segments: HighlightSegment[] = [];
    let cursor = 0;
    for (const match of text.matchAll(pattern)) {
      const index = match.index;
      if (index > cursor) segments.push({ text: text.slice(cursor, index), highlighted: false });
      segments.push({ text: match[0], highlighted: true });
      cursor = index + match[0].length;
    }
    if (cursor < text.length) segments.push({ text: text.slice(cursor), highlighted: false });
    return segments.length ? segments : [{ text, highlighted: false }];
  }
  stateIcon(): string {
    if (this.error() || this.lookup()?.status === 'FAILED') return '!';
    if (this.lookup()?.status === 'EXPIRED') return '⌛';
    return this.loading() ? '↻' : '✓';
  }
  stateTitle(): string {
    if (this.error()) return this.translations.translate('lookup.lookupAttention');
    if (this.lookup()?.errorCode === 'DEFINITION_NOT_FOUND' && this.lookup()?.contexts?.length)
      return this.translations.translate('lookup.definitionContextsAvailable');
    if (this.lookup()?.errorCode === 'CONTEXT_NOT_FOUND' && this.lookup()?.senses?.length)
      return this.translations.translate('lookup.contextsDefinitionAvailable');
    if (this.lookup()?.errorCode === 'DEFINITION_NOT_FOUND')
      return this.translations.translate('lookup.noDefinition');
    if (this.lookup()?.errorCode === 'CONTEXT_NOT_FOUND')
      return this.translations.translate('lookup.noContextsTitle');
    if (this.lookup()?.status === 'FAILED')
      return this.translations.translate('lookup.lookupAttention');
    if (this.lookup()?.status === 'EXPIRED')
      return this.translations.translate('lookup.lookupExpired');
    return this.loading()
      ? this.translations.translate('lookup.lookupProgress')
      : this.translations.translate('lookup.lookupReady');
  }
  stateDescription(): string {
    if (this.error()) return this.error();
    if (this.lookup()?.errorCode === 'DEFINITION_NOT_FOUND' && this.lookup()?.contexts?.length)
      return this.translations.translate('lookup.definitionMissingContexts');
    if (this.lookup()?.errorCode === 'CONTEXT_NOT_FOUND' && this.lookup()?.senses?.length)
      return this.translations.translate('lookup.contextMissingDefinition');
    if (this.lookup()?.errorCode === 'DEFINITION_NOT_FOUND')
      return this.translations.translate('lookup.definitionMissing');
    if (this.lookup()?.errorCode === 'CONTEXT_NOT_FOUND')
      return this.translations.translate('lookup.contextMissing');
    if (this.lookup()?.status === 'FAILED')
      return this.translations.translate('lookup.lookupFailed');
    if (this.lookup()?.status === 'EXPIRED')
      return this.translations.translate('lookup.expiredDescription');
    return this.loading()
      ? this.translations.translate('lookup.loadingDescription')
      : this.translations.translate('lookup.readyDescription');
  }
  private poll(reference: string) {
    this.loading.set(true);
    this.error.set('');
    return timer(0, 2000).pipe(
      switchMap(() => this.api.get(reference)),
      tap((lookup) => {
        this.lookup.set(lookup);
        this.loading.set(lookup.status === 'PENDING');
      }),
      // Include the terminal response in the UI, then stop polling until another lookup starts.
      takeWhile((lookup) => lookup.status === 'PENDING', true),
      catchError(() => {
        this.error.set(this.translations.translate('lookup.unavailable'));
        this.loading.set(false);
        return EMPTY;
      }),
    );
  }
  private formatDuration(milliseconds: number): string {
    const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    return [hours, minutes, seconds].map((value) => value.toString().padStart(2, '0')).join(':');
  }
}
