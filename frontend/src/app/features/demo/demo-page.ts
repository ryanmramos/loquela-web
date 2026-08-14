import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnDestroy,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Meta } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { ConfirmationService } from 'primeng/api';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { EMPTY, Subscription, catchError, finalize, interval, switchMap } from 'rxjs';

import {
  DemoApi,
  DemoCapabilities,
  DemoCard,
  DemoLookup,
  DemoSource,
  DemoSourceState,
  DemoSourceType,
  DemoWorkspace,
} from '@api/api-adapters/demo-api';
import { ApiProblemError } from '@core/errors/api-problem';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { TranslationService } from '@core/i18n/translation.service';
import { Alert } from '@app/shared/components/alert/alert';
import {
  ButtonDirective,
  IconButtonDirective,
} from '@app/shared/components/button/button.directive';
import { AppIcon, AppIconName } from '@app/shared/components/icon/app-icon';
import { StatusBadge, StatusTone } from '@app/shared/components/status-badge/status-badge';
import { Skeleton } from '@app/shared/components/skeleton/skeleton';
import { UiState } from '@app/shared/components/ui-state/ui-state';

type DemoMessageKey =
  | 'demo.errorUnavailable'
  | 'demo.errorBusy'
  | 'demo.errorLimit'
  | 'demo.errorInvalidPdf'
  | 'demo.errorInvalidVideo'
  | 'demo.errorNoCaptions'
  | 'demo.errorSource'
  | 'demo.errorLookup'
  | 'demo.errorGeneral';
type TranslationKey = Parameters<TranslationService['translate']>[0];
type DemoContext = DemoLookup['contexts'][number];
interface HighlightSegment {
  text: string;
  highlighted: boolean;
}

const YOUTUBE_VIDEO_ID = /^[A-Za-z0-9_-]{11}$/u;

@Component({
  selector: 'app-demo-page',
  imports: [
    FormsModule,
    TranslatePipe,
    Alert,
    AppIcon,
    ButtonDirective,
    ConfirmDialog,
    IconButtonDirective,
    StatusBadge,
    Skeleton,
    UiState,
  ],
  templateUrl: './demo-page.html',
  styleUrl: './demo-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DemoPage implements OnInit, OnDestroy {
  readonly api = inject(DemoApi);
  private readonly router = inject(Router);
  private readonly meta = inject(Meta);
  private readonly translations = inject(TranslationService);
  private readonly confirmations = inject(ConfirmationService);
  private readonly destroyRef = inject(DestroyRef);
  private polling?: Subscription;

  readonly capabilities = signal<DemoCapabilities | null>(null);
  readonly capabilitiesLoadFailed = signal(false);
  readonly workspace = signal<DemoWorkspace | null>(null);
  readonly lookup = signal<DemoLookup | null>(null);
  readonly busy = signal(false);
  readonly errorMessage = signal<DemoMessageKey | null>(null);
  readonly notice = signal<'demo.alreadyAdded' | 'demo.cardAdded' | null>(null);
  readonly undo = signal<{ reference: string; token: string } | null>(null);
  readonly downloadStatus = signal<'demo.downloadStarting' | null>(null);
  readonly sourceTypes: readonly DemoSourceType[] = ['PASTED_TEXT', 'PDF', 'YOUTUBE'];

  sourceLanguage = 'es';
  targetLanguage = 'en';
  selectedSourceType: DemoSourceType = 'PASTED_TEXT';
  pastedText = '';
  youtubeUrl = '';
  lookupTerm = '';

  ngOnInit(): void {
    this.meta.updateTag({ name: 'robots', content: 'noindex, nofollow' });
    this.loadCapabilities();
  }

  retryCapabilities(): void {
    this.loadCapabilities();
  }

  private loadCapabilities(): void {
    this.capabilitiesLoadFailed.set(false);
    this.errorMessage.set(null);
    this.api
      .capabilities()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (value) => {
          this.errorMessage.set(null);
          this.capabilities.set(value);
        },
        error: (error) => {
          this.capabilitiesLoadFailed.set(true);
          this.handleError(error);
        },
      });
  }

  ngOnDestroy(): void {
    this.polling?.unsubscribe();
    this.meta.removeTag("name='robots'");
  }

  start(): void {
    this.run(this.api.start(), (workspace) => this.acceptWorkspace(workspace));
  }

  updateLanguages(): void {
    const current = this.workspace();
    const sourceChanged = current !== null && current.sourceLanguage !== this.sourceLanguage;
    if (sourceChanged && current.sources.length > 0) {
      this.confirm(
        'demo.confirmLanguageChangeTitle',
        'demo.confirmLanguageChange',
        'demo.applyLanguages',
        () => this.applyLanguages(true),
      );
      return;
    }
    this.applyLanguages(false);
  }

  confirmReset(): void {
    this.confirm('demo.confirmResetTitle', 'demo.confirmReset', 'demo.startOver', () => {
      this.run(this.api.reset(), (workspace) => {
        this.lookup.set(null);
        this.acceptWorkspace(workspace);
      });
    });
  }

  confirmExit(): void {
    if (!this.workspace()?.cards.length) {
      this.exitWorkspace();
      return;
    }
    this.confirm('demo.confirmExitTitle', 'demo.confirmExit', 'demo.exit', () =>
      this.exitWorkspace(),
    );
  }

  confirmSignIn(): void {
    if (!this.workspace()?.cards.length) {
      this.signIn();
      return;
    }
    this.confirm('demo.confirmSignInTitle', 'demo.confirmSignIn', 'demo.signIn', () =>
      this.signIn(),
    );
  }

  private applyLanguages(confirmSourceChange: boolean): void {
    this.run(
      this.api.languages(this.sourceLanguage, this.targetLanguage, confirmSourceChange),
      (workspace) => this.acceptWorkspace(workspace),
    );
  }

  addPastedText(): void {
    this.run(this.api.pastedText(this.pastedText), (workspace) => {
      this.pastedText = '';
      this.acceptWorkspace(workspace);
    });
  }

  addPdf(input: HTMLInputElement): void {
    const file = input.files?.[0];
    if (!file) return;
    this.run(this.api.pdf(file), (workspace) => {
      input.value = '';
      this.acceptWorkspace(workspace);
    });
  }

  addYouTube(): void {
    this.run(this.api.youtube(this.youtubeUrl), (workspace) => {
      this.youtubeUrl = '';
      this.acceptWorkspace(workspace);
    });
  }

  removeSource(type: DemoSourceType): void {
    this.run(this.api.deleteSource(type), (workspace) => this.acceptWorkspace(workspace));
  }

  retrySource(type: DemoSourceType): void {
    this.run(this.api.retrySource(type), (workspace) => this.acceptWorkspace(workspace));
  }

  lookUp(): void {
    this.run(this.api.lookup(this.lookupTerm), (lookup) => {
      this.lookup.set(lookup);
      this.refresh();
    });
  }

  saveDefinition(): void {
    const lookup = this.lookup();
    if (lookup) this.save(lookup.reference, 'DEFINITION');
  }

  saveContext(reference: string): void {
    const lookup = this.lookup();
    if (lookup) this.save(lookup.reference, 'CONTEXT', reference);
  }

  removeCard(card: DemoCard): void {
    this.run(this.api.deleteCard(card.reference), (result) => {
      this.undo.set({ reference: card.reference, token: result.undoToken });
      this.refresh();
    });
  }

  undoCard(): void {
    const undo = this.undo();
    if (!undo) return;
    this.run(this.api.undoCard(undo.reference, undo.token), () => {
      this.undo.set(null);
      this.refresh();
    });
  }

  exportDeck(): void {
    this.run(this.api.exportDownload(), (file) => {
      this.downloadStatus.set('demo.downloadStarting');
      this.downloadDeck(file);
    });
  }

  downloadDeck(file: Blob): void {
    const url = URL.createObjectURL(file);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'loquela-demo.apkg';
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url));
  }

  private exitWorkspace(): void {
    this.run(this.api.exit(), () => void this.router.navigate(['/login']));
  }

  private signIn(): void {
    window.location.assign('/api/v1/auth/google?returnTo=%2Fapp');
  }

  source(type: DemoSourceType) {
    return this.workspace()?.sources.find((value) => value.type === type);
  }

  selectSource(type: DemoSourceType): void {
    this.selectedSourceType = type;
  }

  sourceIcon(type: DemoSourceType): AppIconName {
    return ({ PASTED_TEXT: 'type', PDF: 'document', YOUTUBE: 'video' } as const)[type];
  }

  sourceStateTone(state: DemoSourceState): StatusTone {
    return ({ QUEUED: 'info', PROCESSING: 'info', READY: 'success', FAILED: 'danger' } as const)[
      state
    ];
  }

  sourceTypeLabel(type: DemoSourceType): string {
    return this.translations.translate(
      (
        {
          PASTED_TEXT: 'demo.source.PASTED_TEXT',
          PDF: 'demo.source.PDF',
          YOUTUBE: 'demo.source.YOUTUBE',
        } as const
      )[type],
    );
  }

  sourceDisplayName(source: DemoSource): string {
    if (
      source.type === 'YOUTUBE' &&
      source.state === 'READY' &&
      source.label.trim() !== '' &&
      source.label !== 'YouTube video'
    ) {
      return source.label;
    }
    return this.sourceTypeLabel(source.type);
  }

  sourceFailureGuidance(code: string | undefined): string {
    return this.translations.translate(this.messageForCode(code));
  }

  youtubeThumbnail(source: DemoSource): string | null {
    const videoId = source.type === 'YOUTUBE' ? this.youtubeVideoId(source.youtubeVideoId) : null;
    return videoId ? `https://i.ytimg.com/vi/${videoId}/mqdefault.jpg` : null;
  }

  youtubeContextUrl(context: DemoContext): string | null {
    if (context.sourceType !== 'YOUTUBE') return null;
    const videoId = this.youtubeVideoId(context.location['videoId']);
    const startMs = this.contextStartMs(context);
    return videoId && startMs !== null
      ? `https://www.youtube.com/watch?v=${videoId}&t=${Math.floor(startMs / 1_000)}s`
      : null;
  }

  contextTimestamp(context: DemoContext): string {
    const startMs = this.contextStartMs(context);
    if (startMs === null) return '';
    const totalSeconds = Math.floor(startMs / 1_000);
    const minutes = Math.floor(totalSeconds / 60);
    return `${minutes}:${String(totalSeconds % 60).padStart(2, '0')}`;
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

  private youtubeVideoId(value: unknown): string | null {
    return typeof value === 'string' && YOUTUBE_VIDEO_ID.test(value) ? value : null;
  }

  private contextStartMs(context: DemoContext): number | null {
    const value = context.location['playbackStartMs'] ?? context.location['startMs'];
    return typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value : null;
  }

  languageName(tag: string): string {
    try {
      return (
        new Intl.DisplayNames([this.translations.language()], { type: 'language' }).of(tag) ?? tag
      );
    } catch {
      return tag;
    }
  }
  sourceLanguageOptions(): string[] {
    return [
      ...new Set(
        (this.capabilities()?.supportedLanguagePairs ?? []).map((pair) => pair.split(':')[0]),
      ),
    ];
  }

  targetLanguageOptions(): string[] {
    return (this.capabilities()?.supportedLanguagePairs ?? [])
      .filter((pair) => pair.split(':')[0] === this.sourceLanguage)
      .map((pair) => pair.split(':')[1]);
  }

  sourceLanguageChanged(): void {
    if (!this.targetLanguageOptions().includes(this.targetLanguage)) {
      this.targetLanguage = this.targetLanguageOptions()[0] ?? '';
    }
  }

  sourceStateKey(
    state: DemoSourceState,
  ): 'demo.sourceQueued' | 'demo.sourceProcessing' | 'demo.sourceReady' | 'demo.sourceFailed' {
    const keys = {
      QUEUED: 'demo.sourceQueued',
      PROCESSING: 'demo.sourceProcessing',
      READY: 'demo.sourceReady',
      FAILED: 'demo.sourceFailed',
    } as const;
    return keys[state];
  }

  private save(
    lookupReference: string,
    kind: 'DEFINITION' | 'CONTEXT',
    selectionReference?: string,
  ): void {
    this.run(this.api.saveCard(lookupReference, kind, selectionReference), (result) => {
      this.notice.set(result.alreadyAdded ? 'demo.alreadyAdded' : 'demo.cardAdded');
      this.refresh();
    });
  }

  private refresh(): void {
    this.api
      .workspace()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (workspace) => this.acceptWorkspace(workspace),
        error: (error) => this.handleError(error),
      });
  }

  private acceptWorkspace(workspace: DemoWorkspace): void {
    this.workspace.set(workspace);
    this.sourceLanguage = workspace.sourceLanguage;
    this.targetLanguage = workspace.targetLanguage;
    if (workspace.latestLookup) this.lookup.set(workspace.latestLookup);
    const processing = workspace.sources.some(
      (source) => source.state === 'QUEUED' || source.state === 'PROCESSING',
    );
    if (processing && !this.polling) {
      this.polling = interval(1_000)
        .pipe(
          switchMap(() => this.api.workspace()),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe({
          next: (value) => this.acceptWorkspace(value),
          error: (error) => {
            this.polling = undefined;
            this.handleError(error);
          },
        });
    } else if (!processing) {
      this.polling?.unsubscribe();
      this.polling = undefined;
    }
  }

  private run<T>(request: import('rxjs').Observable<T>, next: (value: T) => void): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.notice.set(null);
    this.errorMessage.set(null);
    request
      .pipe(
        finalize(() => this.busy.set(false)),
        catchError((error) => {
          this.handleError(error);
          return EMPTY;
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(next);
  }

  private handleError(error: unknown): void {
    const code = error instanceof ApiProblemError ? error.problem.code : 'DEMO_ERROR';
    if (code === 'DEMO_EXPIRED') {
      this.workspace.set(null);
      void this.router.navigate(['/login'], { queryParams: { reason: 'demo-expired' } });
      return;
    }
    this.errorMessage.set(this.messageForCode(code));
  }

  private messageForCode(code: string | undefined): DemoMessageKey {
    const keys: Record<string, DemoMessageKey> = {
      DEMO_UNAVAILABLE: 'demo.errorUnavailable',
      DEMO_INGESTION_DISABLED: 'demo.errorUnavailable',
      DEMO_BUSY: 'demo.errorBusy',
      DEMO_RATE_LIMITED: 'demo.errorBusy',
      DEMO_DECK_FULL: 'demo.errorLimit',
      DEMO_DECK_EMPTY: 'demo.errorLimit',
      LOOKUP_LIMIT_REACHED: 'demo.errorLimit',
      DEMO_EXPORT_LIMIT_REACHED: 'demo.errorLimit',
      DEMO_TOTAL_TEXT_LIMIT: 'demo.errorLimit',
      PASTED_TEXT_TOO_LARGE: 'demo.errorLimit',
      PDF_TOO_LARGE: 'demo.errorLimit',
      PDF_TOO_MANY_PAGES: 'demo.errorLimit',
      PDF_TEXT_TOO_LARGE: 'demo.errorLimit',
      YOUTUBE_TOO_LONG: 'demo.errorLimit',
      INVALID_PDF: 'demo.errorInvalidPdf',
      ENCRYPTED_DOCUMENT: 'demo.errorInvalidPdf',
      MEDIA_TYPE_MISMATCH: 'demo.errorInvalidPdf',
      PDF_NEEDS_OCR: 'demo.errorInvalidPdf',
      INVALID_YOUTUBE_URL: 'demo.errorInvalidVideo',
      YOUTUBE_VIDEO_UNAVAILABLE: 'demo.errorInvalidVideo',
      NO_VIDEO_CAPTIONS: 'demo.errorNoCaptions',
      INVALID_LOOKUP_TERM: 'demo.errorLookup',
      DEFINITION_NOT_FOUND: 'demo.errorLookup',
      DEMO_INGESTION_FAILED: 'demo.errorSource',
      YOUTUBE_PROVIDER_TEMPORARY_FAILURE: 'demo.errorSource',
      YTDLP_EXECUTION_FAILED: 'demo.errorSource',
      YOUTUBE_CAPTION_IO_FAILED: 'demo.errorSource',
    };
    return keys[code ?? ''] ?? 'demo.errorGeneral';
  }

  private confirm(
    titleKey: TranslationKey,
    messageKey: TranslationKey,
    acceptLabelKey: TranslationKey,
    accept: () => void,
  ): void {
    this.confirmations.confirm({
      header: this.translations.translate(titleKey),
      message: this.translations.translate(messageKey),
      closable: true,
      acceptLabel: this.translations.translate(acceptLabelKey),
      rejectLabel: this.translations.translate('common.cancel'),
      acceptButtonProps: { severity: 'danger', styleClass: 'loquela-confirm-dialog__accept' },
      rejectButtonProps: {
        outlined: true,
        severity: 'secondary',
        styleClass: 'loquela-confirm-dialog__reject',
      },
      accept,
    });
  }
}
