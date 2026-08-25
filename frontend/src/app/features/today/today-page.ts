import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Select } from 'primeng/select';
import { EMPTY, Subscription, catchError, switchMap, takeWhile, timer } from 'rxjs';

import { CardApi } from '@api/api-adapters/card-api';
import { DashboardApi, DashboardSnapshot } from '@api/api-adapters/dashboard-api';
import {
  BrowserDeckOptionDto,
  BrowserLookupDto,
  ContextDto,
  LookupApi,
} from '@api/api-adapters/lookup-api';
import { SourceApi, SourceSummary } from '@api/api-adapters/source-api';
import { AppIcon } from '@app/shared/components/icon/app-icon';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { SearchInput } from '@app/shared/components/search-input/search-input';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { TranslationService } from '@core/i18n/translation.service';

type FirstStep = 'today' | 'search' | 'context' | 'review' | 'saved';
type SearchStatus = 'idle' | 'loading' | 'ready' | 'error';
type SaveStatus = 'idle' | 'saving' | 'saved' | 'error';

interface HighlightSegment {
  readonly text: string;
  readonly highlighted: boolean;
}

@Component({
  selector: 'app-today-page',
  imports: [
    AppIcon,
    ButtonDirective,
    ReactiveFormsModule,
    RouterLink,
    SearchInput,
    Select,
    TranslatePipe,
  ],
  templateUrl: './today-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TodayPage {
  private readonly dashboardApi = inject(DashboardApi);
  private readonly lookupApi = inject(LookupApi);
  private readonly sourceApi = inject(SourceApi);
  private readonly cardApi = inject(CardApi);
  private readonly translations = inject(TranslationService);
  private readonly destroyRef = inject(DestroyRef);
  private lookupSubscription?: Subscription;

  readonly step = signal<FirstStep>('today');
  readonly dashboard = signal<DashboardSnapshot | null>(null);
  readonly sources = signal<SourceSummary[]>([]);
  readonly dashboardLoading = signal(true);
  readonly sourceLoading = signal(true);
  readonly pageError = signal(false);
  readonly lookup = signal<BrowserLookupDto | null>(null);
  readonly selectedContext = signal<ContextDto | null>(null);
  readonly searchStatus = signal<SearchStatus>('idle');
  readonly saveStatus = signal<SaveStatus>('idle');
  readonly decks = signal<BrowserDeckOptionDto[]>([]);
  readonly decksLoading = signal(false);
  readonly errorMessage = signal('');
  readonly savedCard = signal<{
    term: string;
    definition: string;
    exampleText: string;
    deckName: string;
  } | null>(null);
  readonly selectedKinds = signal<string[]>([]);

  readonly searchForm = new FormGroup({
    term: new FormControl('', {
      nonNullable: true,
      validators: [
        (control) => Validators.required(control),
        (control) => Validators.maxLength(256)(control),
      ],
    }),
  });
  readonly reviewForm = new FormGroup({
    meaning: new FormControl('', {
      nonNullable: true,
      validators: (control) => Validators.required(control),
    }),
    example: new FormControl('', { nonNullable: true }),
    deckReference: new FormControl('', {
      nonNullable: true,
      validators: (control) => Validators.required(control),
    }),
  });

  readonly sourceTypeFilters = [
    { value: 'YOUTUBE_VIDEO', label: 'lookup.videoType' },
    { value: 'TIMED_TRANSCRIPT', label: 'lookup.timedTranscriptType' },
    { value: 'EPUB', label: 'lookup.epubType' },
    { value: 'PDF', label: 'lookup.pdfType' },
    { value: 'PASTED_TEXT', label: 'lookup.pastedType' },
  ] as const;

  constructor() {
    this.loadToday();
  }

  loadToday(): void {
    this.dashboardLoading.set(true);
    this.sourceLoading.set(true);
    this.pageError.set(false);
    this.dashboardApi
      .get()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (snapshot) => {
          this.dashboard.set(snapshot);
          this.dashboardLoading.set(false);
        },
        error: () => {
          this.dashboardLoading.set(false);
          this.pageError.set(true);
        },
      });
    this.sourceApi
      .list({ page: 0, pageSize: 20, status: 'READY', sort: 'UPDATED_AT', direction: 'DESC' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.sources.set(page.items ?? []);
          this.sourceLoading.set(false);
        },
        error: () => {
          this.sourceLoading.set(false);
          this.pageError.set(true);
        },
      });
  }

  openSearch(term = ''): void {
    this.searchForm.controls.term.setValue(term);
    this.lookup.set(null);
    this.selectedContext.set(null);
    this.errorMessage.set('');
    this.searchStatus.set('idle');
    this.step.set('search');
  }

  toggleKind(value: string): void {
    this.selectedKinds.update((current) =>
      current.includes(value) ? current.filter((kind) => kind !== value) : [...current, value],
    );
  }

  kindSelected(value: string): boolean {
    return this.selectedKinds().includes(value);
  }

  startSearch(): void {
    if (this.searchForm.invalid) {
      this.searchForm.controls.term.markAsTouched();
      return;
    }
    const term = this.searchForm.controls.term.value.trim();
    this.step.set('search');
    this.searchStatus.set('loading');
    this.lookup.set(null);
    this.selectedContext.set(null);
    this.errorMessage.set('');
    this.lookupSubscription?.unsubscribe();
    this.lookupApi
      .create(term, [], this.selectedKinds())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (accepted) => this.pollLookup(accepted.reference),
        error: () => {
          this.searchStatus.set('error');
          this.errorMessage.set(this.translations.translate('today.searchError'));
        },
      });
  }

  chooseContext(context: ContextDto): void {
    this.selectedContext.set(context);
    this.errorMessage.set('');
    this.step.set('context');
  }

  beginReview(): void {
    const result = this.lookup();
    const context = this.selectedContext();
    if (!result) return;
    const definition = (result.senses ?? [])
      .map((sense) => [sense.partOfSpeech, sense.definition].filter(Boolean).join(': '))
      .join('\n');
    this.reviewForm.setValue({
      meaning: definition,
      example: context?.displayText ?? '',
      deckReference: this.decks()[0]?.reference ?? '',
    });
    this.step.set('review');
    this.loadDecks();
  }

  loadDecks(): void {
    if (this.decks().length) return;
    this.decksLoading.set(true);
    this.lookupApi
      .decks()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.decks.set(page.items ?? []);
          this.decksLoading.set(false);
          if (!this.reviewForm.controls.deckReference.value && page.items?.[0]?.reference) {
            this.reviewForm.controls.deckReference.setValue(page.items[0].reference);
          }
        },
        error: () => {
          this.decksLoading.set(false);
          this.errorMessage.set(this.translations.translate('today.loadError'));
        },
      });
  }

  saveCard(): void {
    if (this.reviewForm.invalid || this.saveStatus() === 'saving') return;
    const result = this.lookup();
    if (!result?.term) return;
    const form = this.reviewForm.getRawValue();
    this.saveStatus.set('saving');
    this.errorMessage.set('');
    this.cardApi
      .create({
        deckReference: form.deckReference,
        term: result.lemma ?? result.term,
        definition: form.meaning.trim(),
        exampleText: form.example.trim() || undefined,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.savedCard.set({
            term: result.lemma ?? result.term ?? '',
            definition: form.meaning.trim(),
            exampleText: form.example.trim(),
            deckName:
              this.decks().find((deck) => deck.reference === form.deckReference)?.name ??
              form.deckReference,
          });
          this.saveStatus.set('saved');
          this.step.set('saved');
        },
        error: () => {
          this.saveStatus.set('error');
          this.errorMessage.set(this.translations.translate('today.saveError'));
        },
      });
  }

  backToToday(): void {
    this.step.set('today');
    this.errorMessage.set('');
  }

  returnToCorpus(): void {
    this.openSearch(this.lookup()?.term ?? '');
  }

  backToSearch(): void {
    this.step.set('search');
    this.errorMessage.set('');
  }

  backToContext(): void {
    this.step.set('context');
    this.errorMessage.set('');
  }

  sourceLink(context: ContextDto | null): string[] {
    return context?.sourceReference ? ['/app/sources', context.sourceReference] : ['/app/sources'];
  }

  sourceLocation(context: ContextDto | null): string {
    if (!context) {
      return this.translations.translate('today.dictionaryDefinition');
    }
    if (context.youtubeUrl && context.startMs !== undefined) {
      return `${this.translations.translate('lookup.video')} · ${this.formatDuration(context.startMs)}`;
    }
    if (context.startMs !== undefined) {
      return `${this.sourceKindLabel(context.sourceKind)} · ${this.formatDuration(context.startMs)}`;
    }
    if (context.pageNumber !== undefined) {
      return `${this.sourceKindLabel(context.sourceKind)} · ${this.translations.translate('lookup.page')} ${context.pageNumber}`;
    }
    return `${this.sourceKindLabel(context.sourceKind)} · ${this.translations.translate('lookup.context')} ${context.ordinal ?? ''}`;
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

  highlightedContext(text: string, term: string): HighlightSegment[] {
    const needle = term.trim();
    if (!needle) return [{ text, highlighted: false }];
    const pattern = new RegExp(needle.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&'), 'giu');
    const segments: HighlightSegment[] = [];
    let cursor = 0;
    for (const match of text.matchAll(pattern)) {
      const index = match.index ?? 0;
      if (index > cursor) segments.push({ text: text.slice(cursor, index), highlighted: false });
      segments.push({ text: match[0], highlighted: true });
      cursor = index + match[0].length;
    }
    if (cursor < text.length) segments.push({ text: text.slice(cursor), highlighted: false });
    return segments.length ? segments : [{ text, highlighted: false }];
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

  formatHeadingDate(value?: string): string {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    return new Intl.DateTimeFormat(this.translations.language(), {
      weekday: 'long',
      month: 'long',
      day: 'numeric',
    }).format(date);
  }

  private pollLookup(reference: string): void {
    this.lookupSubscription?.unsubscribe();
    this.lookupSubscription = timer(0, 2000)
      .pipe(
        switchMap(() => this.lookupApi.get(reference)),
        takeWhile((result) => result.status === 'PENDING', true),
        takeUntilDestroyed(this.destroyRef),
        catchError(() => {
          this.searchStatus.set('error');
          this.errorMessage.set(this.translations.translate('today.loadError'));
          return EMPTY;
        }),
      )
      .subscribe((result) => {
        this.lookup.set(result);
        if (result.status === 'READY') this.searchStatus.set('ready');
        if (result.status === 'FAILED' || result.status === 'EXPIRED') {
          this.searchStatus.set('error');
          this.errorMessage.set(this.translations.translate('today.loadError'));
        }
      });
  }

  private formatDuration(milliseconds: number): string {
    const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    return [hours, minutes, seconds].map((value) => value.toString().padStart(2, '0')).join(':');
  }
}
