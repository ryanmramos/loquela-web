import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
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
import { SourceApi, SourceSummary } from '@api/api-adapters/source-api';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { UiState } from '@app/shared/components/ui-state/ui-state';
import { ToastService } from '@app/shared/components/toast/toast.service';
import { LookupSourceFilters } from './source-filters/lookup-source-filters';

interface HighlightSegment {
  readonly text: string;
  readonly highlighted: boolean;
}

@Component({
  selector: 'app-lookup-page',
  imports: [ReactiveFormsModule, ButtonDirective, LookupSourceFilters, RouterLink, UiState],
  templateUrl: './lookup-page.html',
  styleUrl: './lookup-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LookupPage {
  private readonly api = inject(LookupApi);
  private readonly sourceApi = inject(SourceApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly toasts = inject(ToastService);
  readonly lookup = signal<BrowserLookupDto | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly decks = signal<BrowserDeckOptionDto[]>([]);
  readonly sources = signal<SourceSummary[]>([]);
  readonly selectedContext = signal<number | null>(null);
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
        error: () => this.error.set('Ready sources could not be loaded.'),
      });
  }
  submit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set('');
    const v = this.form.getRawValue();
    this.api.create(v.term.trim(), v.sourceReferences, v.sourceKinds).subscribe({
      next: (a) =>
        void this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { lookup: a.reference },
        }),
      error: () => {
        this.loading.set(false);
        this.error.set('We could not start that lookup.');
      },
    });
  }
  chooseContext(number: number): void {
    this.selectedContext.set(number);
    this.api.decks().subscribe({
      next: (decks) => {
        this.decks.set(decks);
        if (decks[0]?.reference) this.saveForm.controls.deckReference.setValue(decks[0].reference);
      },
      error: () => this.error.set('Active decks could not be loaded.'),
    });
  }
  save(): void {
    const lookup = this.lookup(),
      context = this.selectedContext();
    if (!lookup?.reference || !context || this.saveForm.invalid) return;
    this.saving.set(true);
    this.api.save(lookup.reference, context, this.saveForm.getRawValue().deckReference).subscribe({
      next: () => {
        this.saving.set(false);
        this.selectedContext.set(null);
        this.toasts.show(
          'Card saved',
          'Your contextual card and source provenance were saved.',
          'success',
        );
      },
      error: () => {
        this.saving.set(false);
        this.error.set('This result can no longer be saved.');
      },
    });
  }
  closeSave(): void {
    this.selectedContext.set(null);
  }
  chooseDeck(reference: string): void {
    this.saveForm.controls.deckReference.setValue(reference);
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
    return kind === 'YOUTUBE_VIDEO' ? 'Video' : (kind ?? 'Source').replaceAll('_', ' ');
  }
  sourceIcon(kind: string | undefined): string {
    if (kind === 'YOUTUBE_VIDEO') return '▶';
    if (kind === 'PDF') return 'PDF';
    if (kind === 'EPUB') return 'EPUB';
    return 'T';
  }
  sourceLocation(context: NonNullable<BrowserLookupDto['contexts']>[number]): string {
    if (context.youtubeUrl && context.startMs !== undefined)
      return `Video · ${this.formatDuration(context.startMs)}`;
    if (context.pageNumber !== undefined)
      return `${this.sourceKindLabel(context.sourceKind)} · page ${context.pageNumber}`;
    return `${this.sourceKindLabel(context.sourceKind)} · context ${context.ordinal ?? ''}`;
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
    if (this.error()) return 'Lookup needs attention';
    if (this.lookup()?.errorCode === 'DEFINITION_NOT_FOUND') return 'No definition found';
    if (this.lookup()?.errorCode === 'CONTEXT_NOT_FOUND') return 'No matching contexts';
    if (this.lookup()?.status === 'FAILED') return 'Lookup needs attention';
    if (this.lookup()?.status === 'EXPIRED') return 'Lookup expired';
    return this.loading() ? 'Lookup in progress' : 'Lookup ready';
  }
  stateDescription(): string {
    if (this.error()) return this.error();
    if (this.lookup()?.errorCode === 'DEFINITION_NOT_FOUND')
      return 'The dictionary did not return a definition for this term in your current language pair.';
    if (this.lookup()?.errorCode === 'CONTEXT_NOT_FOUND')
      return 'A definition was found, but none of your selected ready sources contained a matching context.';
    if (this.lookup()?.status === 'FAILED')
      return 'The dictionary could not complete this lookup. Your sources are safe.';
    if (this.lookup()?.status === 'EXPIRED')
      return 'Run the lookup again to create a fresh snapshot.';
    return this.loading()
      ? 'Finding dictionary senses and matching ready sources…'
      : 'Results use your current language pair and ready sources.';
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
        this.error.set('That lookup is unavailable.');
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
