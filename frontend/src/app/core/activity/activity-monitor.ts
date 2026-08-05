import { inject, Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';

import { ActivityApi, ActivityItem } from '@api/api-adapters/activity-api';
import { ToastService } from '@app/shared/components/toast/toast.service';
import { TranslationService } from '@core/i18n/translation.service';
import { ShellDashboardStore } from '@core/layout/shell-dashboard-store';

const INITIAL_DELAY_MS = 2_000;
const MAX_DELAY_MS = 30_000;
const JITTER_RATIO = 0.2;

/**
 * Owns one authenticated-shell polling clock. Feature pages register accepted work here instead
 * of creating per-row timers, and initial server state is deliberately never announced.
 */
@Injectable({ providedIn: 'root' })
export class ActivityMonitor {
  private readonly api = inject(ActivityApi);
  private readonly toasts = inject(ToastService);
  private readonly translations = inject(TranslationService);
  private readonly shellDashboard = inject(ShellDashboardStore);
  private readonly itemState = signal<ReadonlyMap<string, ActivityItem>>(new Map());
  private readonly pausedState = signal(false);
  private timerId?: ReturnType<typeof setTimeout>;
  private delayMs = INITIAL_DELAY_MS;
  private running = false;
  private requestInFlight = false;
  private retryAfterMs?: number;
  private requestSubscription?: Subscription;

  readonly items = this.itemState.asReadonly();
  readonly paused = this.pausedState.asReadonly();

  start(): void {
    if (this.running) return;
    this.running = true;
    document.addEventListener('visibilitychange', this.handleAvailabilityChange);
    window.addEventListener('online', this.handleAvailabilityChange);
    window.addEventListener('offline', this.handleAvailabilityChange);
    if (this.canPoll()) this.refreshBaseline();
    else this.pause();
  }

  stop(): void {
    this.running = false;
    this.requestInFlight = false;
    this.requestSubscription?.unsubscribe();
    this.requestSubscription = undefined;
    this.clearTimer();
    document.removeEventListener('visibilitychange', this.handleAvailabilityChange);
    window.removeEventListener('online', this.handleAvailabilityChange);
    window.removeEventListener('offline', this.handleAvailabilityChange);
    this.itemState.set(new Map());
    this.pausedState.set(false);
    this.shellDashboard.updateActivity(null);
  }

  /** Reloads the bounded active baseline after a browser feature accepts new background work. */
  refreshBaseline(): void {
    if (!this.running || !this.canPoll() || this.requestInFlight) return;
    this.requestInFlight = true;
    this.requestSubscription = this.api.list({ lifecycle: 'ACTIVE', limit: 50 }).subscribe({
      next: (page) => {
        this.requestInFlight = false;
        this.pausedState.set(false);
        this.shellDashboard.updateActivity(page.activeCount);
        this.merge(page.items, false);
        this.delayMs = INITIAL_DELAY_MS;
        this.scheduleIfActive();
      },
      error: (error: unknown) => {
        this.requestInFlight = false;
        this.handleFailure(error);
        this.scheduleIfActive();
      },
    });
  }

  register(items: readonly ActivityItem[]): void {
    this.merge(items, false);
    this.scheduleIfActive(true);
  }

  private readonly handleAvailabilityChange = (): void => {
    if (!this.running) return;
    if (!this.canPoll()) {
      this.pause();
      return;
    }
    this.pausedState.set(false);
    this.delayMs = INITIAL_DELAY_MS;
    if (this.activeReferences().length > 0) this.pollNow();
    else this.refreshBaseline();
  };

  private pollNow(): void {
    this.clearTimer();
    if (!this.running || !this.canPoll() || this.requestInFlight) return;
    const references = this.activeReferences();
    if (references.length === 0) return;
    this.requestInFlight = true;
    this.requestSubscription = this.api.statuses(references).subscribe({
      next: (result) => {
        this.requestInFlight = false;
        this.shellDashboard.updateActivity(result.activeCount);
        this.removeMissing(references, result.items);
        const transitioned = this.merge(result.items, true);
        if (transitioned) this.delayMs = INITIAL_DELAY_MS;
        else this.increaseBackoff();
        this.scheduleIfActive();
      },
      error: (error: unknown) => {
        this.requestInFlight = false;
        this.handleFailure(error);
        this.scheduleIfActive();
      },
    });
  }

  private merge(items: readonly ActivityItem[], announceTransitions: boolean): boolean {
    const next = new Map(this.itemState());
    let completed = 0;
    let failed = 0;
    for (const item of items) {
      const previous = next.get(item.reference);
      if (announceTransitions && previous?.lifecycle === 'ACTIVE' && item.lifecycle !== 'ACTIVE') {
        if (item.lifecycle === 'FAILED') failed += 1;
        else completed += 1;
      }
      next.set(item.reference, item);
    }
    this.itemState.set(next);
    if (completed > 0 || failed > 0) this.announce(completed, failed);
    return completed > 0 || failed > 0;
  }

  private removeMissing(requested: readonly string[], returned: readonly ActivityItem[]): void {
    const recognized = new Set(returned.map((item) => item.reference));
    if (requested.every((reference) => recognized.has(reference))) return;
    const next = new Map(this.itemState());
    requested
      .filter((reference) => !recognized.has(reference))
      .forEach((reference) => next.delete(reference));
    this.itemState.set(next);
  }

  private announce(completed: number, failed: number): void {
    const parts: string[] = [];
    if (completed > 0) {
      parts.push(this.translations.translate('activity.completedCount', { count: completed }));
    }
    if (failed > 0) {
      parts.push(this.translations.translate('activity.failedCount', { count: failed }));
    }
    this.toasts.show(
      this.translations.translate('activity.notificationTitle'),
      parts.join(' '),
      failed > 0 ? 'warning' : 'success',
    );
  }

  private scheduleIfActive(immediate = false): void {
    this.clearTimer();
    if (!this.running || !this.canPoll() || this.activeReferences().length === 0) return;
    const delay = immediate ? 0 : (this.retryAfterMs ?? this.jitteredDelay());
    this.retryAfterMs = undefined;
    this.timerId = setTimeout(() => this.pollNow(), delay);
  }

  private activeReferences(): string[] {
    return [...this.itemState().values()]
      .filter((item) => item.lifecycle === 'ACTIVE')
      .slice(0, 50)
      .map((item) => item.reference);
  }

  private increaseBackoff(): void {
    this.delayMs = Math.min(this.delayMs * 2, MAX_DELAY_MS);
  }

  private handleFailure(error: unknown): void {
    this.increaseBackoff();
    if (!(error instanceof HttpErrorResponse)) return;
    const header = error.headers.get('Retry-After');
    if (!header) return;
    const seconds = Number(header);
    const dateDelay = Date.parse(header) - Date.now();
    const requested = Number.isFinite(seconds) ? seconds * 1_000 : dateDelay;
    if (Number.isFinite(requested) && requested > 0) {
      // The server may ask for a shorter retry, but never let it bypass the normal ceiling.
      this.retryAfterMs = Math.min(Math.round(requested), MAX_DELAY_MS);
    }
  }

  private jitteredDelay(): number {
    const factor = 1 - JITTER_RATIO + Math.random() * JITTER_RATIO * 2;
    return Math.round(this.delayMs * factor);
  }

  private canPoll(): boolean {
    return navigator.onLine && document.visibilityState === 'visible';
  }

  private pause(): void {
    this.clearTimer();
    this.pausedState.set(true);
  }

  private clearTimer(): void {
    if (this.timerId !== undefined) clearTimeout(this.timerId);
    this.timerId = undefined;
  }
}
