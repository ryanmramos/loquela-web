import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, timer } from 'rxjs';

import { DashboardApi, DashboardSnapshot } from '@api/api-adapters/dashboard-api';
import { AcceptedPastedTextSource, SourceApi } from '@api/api-adapters/source-api';
import { ShellDashboardStore } from '@core/layout/shell-dashboard-store';
import { ActivityMonitor } from '@core/activity/activity-monitor';

type LoadStatus = 'idle' | 'loading' | 'success' | 'error';
type CaptureStatus = 'idle' | 'submitting' | 'success' | 'error';

@Injectable()
export class DashboardState {
  private static readonly STALE_AFTER_MS = 5 * 60 * 1000;

  private readonly dashboardApi = inject(DashboardApi);
  private readonly sourceApi = inject(SourceApi);
  private readonly shellDashboard = inject(ShellDashboardStore);
  private readonly activityMonitor = inject(ActivityMonitor);
  private readonly destroyRef = inject(DestroyRef);
  private readonly loadStatusState = signal<LoadStatus>('idle');
  private readonly snapshotState = signal<DashboardSnapshot | null>(null);
  private readonly captureStatusState = signal<CaptureStatus>('idle');
  private readonly acceptedSourceState = signal<AcceptedPastedTextSource | null>(null);
  private readonly nowState = signal(Date.now());
  private loadSubscription?: Subscription;
  private captureSubscription?: Subscription;

  readonly loadStatus = this.loadStatusState.asReadonly();
  readonly snapshot = this.snapshotState.asReadonly();
  readonly captureStatus = this.captureStatusState.asReadonly();
  readonly acceptedSource = this.acceptedSourceState.asReadonly();
  readonly stale = computed(() => {
    const snapshot = this.snapshotState();
    if (!snapshot) {
      return false;
    }
    const generatedAt = Date.parse(snapshot.generatedAt);
    return (
      !Number.isFinite(generatedAt) || this.nowState() - generatedAt > DashboardState.STALE_AFTER_MS
    );
  });

  constructor() {
    timer(0, 60_000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.nowState.set(Date.now()));
    this.destroyRef.onDestroy(() => {
      this.loadSubscription?.unsubscribe();
      this.captureSubscription?.unsubscribe();
    });
  }

  load(): void {
    this.loadSubscription?.unsubscribe();
    this.loadStatusState.set('loading');
    this.loadSubscription = this.dashboardApi
      .get()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (snapshot) => {
          this.snapshotState.set(snapshot);
          this.shellDashboard.update(
            snapshot.jobs.status === 'AVAILABLE' ? snapshot.jobs.activeJobs : null,
            snapshot.cards.status === 'AVAILABLE' ? snapshot.cards.cardsToStudy : null,
          );
          this.loadStatusState.set('success');
        },
        error: () => {
          this.loadStatusState.set('error');
          this.shellDashboard.update(null, null);
        },
      });
  }

  capture(text: string): void {
    const normalizedText = text.trim();
    if (normalizedText.length === 0 || this.captureStatusState() === 'submitting') {
      return;
    }
    this.captureSubscription?.unsubscribe();
    this.captureStatusState.set('submitting');
    this.acceptedSourceState.set(null);
    this.captureSubscription = this.sourceApi
      .createPastedText(normalizedText)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (source) => {
          this.acceptedSourceState.set(source);
          this.captureStatusState.set('success');
          this.activityMonitor.refreshBaseline();
          this.load();
        },
        error: () => this.captureStatusState.set('error'),
      });
  }
}
