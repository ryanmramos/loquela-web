import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter, Observable, Subscription, switchMap, takeWhile, timer } from 'rxjs';

import { SourceApi, SourceDetails } from '@api/api-adapters/source-api';
import { ApiProblemError } from '@core/errors/api-problem';

type LoadStatus = 'loading' | 'success' | 'not-found' | 'error';
type MutationStatus = 'idle' | 'submitting' | 'error';

@Injectable()
export class SourceDetailsState {
  private static readonly POLL_INTERVAL_MS = 3_000;
  private readonly sourceApi = inject(SourceApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly loadStatusState = signal<LoadStatus>('loading');
  private readonly mutationStatusState = signal<MutationStatus>('idle');
  private readonly sourceState = signal<SourceDetails | null>(null);
  private readonly errorMessageState = signal<string | null>(null);
  private reference = '';
  private pollSubscription?: Subscription;
  private mutationSubscription?: Subscription;

  readonly loadStatus = this.loadStatusState.asReadonly();
  readonly mutationStatus = this.mutationStatusState.asReadonly();
  readonly source = this.sourceState.asReadonly();
  readonly errorMessage = this.errorMessageState.asReadonly();

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.pollSubscription?.unsubscribe();
      this.mutationSubscription?.unsubscribe();
    });
  }

  load(reference: string): void {
    this.reference = reference;
    this.loadStatusState.set('loading');
    this.startPolling();
  }

  refresh(): void {
    this.loadStatusState.set('loading');
    this.startPolling();
  }

  rename(displayName: string): void {
    const source = this.sourceState();
    if (!source || !displayName.trim()) return;
    this.mutate(this.sourceApi.rename(this.reference, displayName.trim(), source.version), false);
  }

  retry(): void {
    this.mutate(this.sourceApi.retry(this.reference), true);
  }

  delete(): void {
    this.mutate(this.sourceApi.delete(this.reference), true);
  }

  private startPolling(): void {
    this.pollSubscription?.unsubscribe();
    this.pollSubscription = timer(0, SourceDetailsState.POLL_INTERVAL_MS)
      .pipe(
        filter(() => this.canPoll()),
        switchMap(() => this.sourceApi.get(this.reference)),
        takeWhile((source) => !this.isTerminal(source.status), true),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (source) => {
          this.sourceState.set(source);
          this.loadStatusState.set('success');
          this.errorMessageState.set(null);
        },
        error: (error: unknown) => {
          this.pollSubscription?.unsubscribe();
          if (error instanceof ApiProblemError && error.problem.status === 404) {
            this.loadStatusState.set('not-found');
          } else {
            this.loadStatusState.set('error');
          }
        },
      });
  }

  private mutate(request: Observable<unknown>, resumePolling: boolean): void {
    if (this.mutationStatusState() === 'submitting') return;
    this.mutationSubscription?.unsubscribe();
    this.mutationStatusState.set('submitting');
    this.errorMessageState.set(null);
    this.mutationSubscription = request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.mutationStatusState.set('idle');
        if (resumePolling) {
          this.startPolling();
        } else {
          this.refresh();
        }
      },
      error: (error: unknown) => {
        this.mutationStatusState.set('error');
        this.errorMessageState.set(
          error instanceof ApiProblemError
            ? (error.problem.detail ?? error.problem.title)
            : 'The source could not be updated. Try again.',
        );
      },
    });
  }

  private canPoll(): boolean {
    return (
      (typeof navigator === 'undefined' || navigator.onLine) &&
      (typeof document === 'undefined' || document.visibilityState === 'visible')
    );
  }

  private isTerminal(status: SourceDetails['status']): boolean {
    return status === 'READY' || status === 'FAILED' || status === 'DELETED';
  }
}
