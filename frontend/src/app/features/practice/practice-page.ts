import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { Deck, DeckApi } from '@api/api-adapters/deck-api';
import { PracticeApi, PracticeRating, PracticeSession } from '@api/api-adapters/practice-api';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { UiState } from '@app/shared/components/ui-state/ui-state';

import { YoutubePracticePlayer } from './youtube-practice-player';

interface RatingOption {
  readonly rating: PracticeRating;
  readonly label: string;
  readonly description: string;
  readonly key: string;
  readonly positive?: boolean;
}

@Component({
  selector: 'app-practice-page',
  imports: [RouterLink, ButtonDirective, UiState, YoutubePracticePlayer],
  templateUrl: './practice-page.html',
  styleUrl: './practice-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PracticePage {
  private readonly practiceApi = inject(PracticeApi);
  private readonly deckApi = inject(DeckApi);
  private readonly focusTarget = viewChild<ElementRef<HTMLElement>>('focusTarget');

  readonly session = signal<PracticeSession | null>(null);
  readonly decks = signal<Deck[]>([]);
  readonly loading = signal(true);
  readonly pending = signal(false);
  readonly error = signal('');
  readonly pendingRating = signal<{ requestId: string; rating: PracticeRating } | null>(null);
  readonly ratings: readonly RatingOption[] = [
    { rating: 'NEEDS_WORK', label: 'Needs work', description: 'Keep practicing', key: '1' },
    { rating: 'ALMOST', label: 'Almost', description: 'Nearly there', key: '2' },
    {
      rating: 'GOT_IT',
      label: 'Got it',
      description: 'Continue',
      key: '3',
      positive: true,
    },
    { rating: 'EASY', label: 'Easy', description: 'Continue', key: '4' },
  ];

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.practiceApi.active().subscribe({
      next: (session) => {
        this.session.set(session);
        this.loadDecks();
      },
      error: () => {
        this.error.set('Practice could not be loaded.');
        this.loadDecks();
      },
    });
  }

  start(deck: Deck): void {
    if (!deck.reference || !deck.cardCount || this.pending()) return;
    this.pending.set(true);
    this.error.set('');
    this.practiceApi.start(deck.reference).subscribe({
      next: (session) => {
        this.session.set(session);
        this.pending.set(false);
        this.focusCurrentCard();
      },
      error: () => {
        this.pending.set(false);
        this.error.set('This deck could not start a practice session.');
      },
    });
  }

  reveal(): void {
    const reference = this.session()?.reference;
    if (!reference || this.pending()) return;
    this.pending.set(true);
    this.error.set('');
    this.practiceApi.reveal(reference).subscribe({
      next: (session) => {
        this.session.set(session);
        this.pending.set(false);
        this.focusCurrentCard();
      },
      error: () => {
        this.pending.set(false);
        this.error.set('The answer could not be revealed. Refresh and try again.');
      },
    });
  }

  rate(rating: PracticeRating): void {
    const reference = this.session()?.reference;
    if (!reference || !this.session()?.answerRevealed || this.pending()) return;
    const operation = { requestId: crypto.randomUUID(), rating };
    this.pendingRating.set(operation);
    this.submitRating(reference, operation);
  }

  retryRating(): void {
    const reference = this.session()?.reference;
    const operation = this.pendingRating();
    if (!reference || !operation || this.pending()) return;
    // Reuse the UUID so an uncertain network retry cannot advance twice.
    this.submitRating(reference, operation);
  }

  end(): void {
    const reference = this.session()?.reference;
    if (
      !reference ||
      this.pending() ||
      !confirm('End this practice session? Its current progress will be discarded.')
    ) {
      return;
    }
    this.pending.set(true);
    this.practiceApi.end(reference).subscribe({
      next: () => {
        this.session.set(null);
        this.pending.set(false);
      },
      error: () => {
        this.pending.set(false);
        this.error.set('The session could not be ended.');
      },
    });
  }

  progress(session: PracticeSession): number {
    const total = session.totalCards ?? 0;
    if (!total) return 0;
    if (session.status === 'COMPLETED') return 100;
    return Math.round((((session.position ?? 1) - 1) / total) * 100);
  }

  @HostListener('document:keydown', ['$event'])
  handleShortcut(event: KeyboardEvent): void {
    if (event.defaultPrevented || this.isTypingTarget(event.target)) return;
    const current = this.session();
    if (current?.status !== 'ACTIVE') return;
    if (event.code === 'Space' && !current.answerRevealed) {
      event.preventDefault();
      this.reveal();
      return;
    }
    if (!current.answerRevealed) return;
    const option = this.ratings.find((item) => item.key === event.key);
    if (option) {
      event.preventDefault();
      this.rate(option.rating);
    }
  }

  private loadDecks(): void {
    this.deckApi.list().subscribe({
      next: (decks) => {
        this.decks.set(decks);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Decks could not be loaded.');
        this.loading.set(false);
      },
    });
  }

  private submitRating(
    reference: string,
    operation: { requestId: string; rating: PracticeRating },
  ): void {
    this.pending.set(true);
    this.error.set('');
    this.practiceApi.rate(reference, operation.requestId, operation.rating).subscribe({
      next: (session) => {
        this.session.set(session);
        this.pendingRating.set(null);
        this.pending.set(false);
        this.focusCurrentCard();
      },
      error: () => {
        this.pending.set(false);
        this.error.set('Your response could not be confirmed. Retry safely with the same request.');
      },
    });
  }

  private focusCurrentCard(): void {
    queueMicrotask(() => this.focusTarget()?.nativeElement.focus());
  }

  private isTypingTarget(target: EventTarget | null): boolean {
    if (!(target instanceof HTMLElement)) return false;
    return (
      target.matches('input, textarea, select, [contenteditable="true"]') ||
      target.closest('[role="dialog"]') !== null
    );
  }
}
