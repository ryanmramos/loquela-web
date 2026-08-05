import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';

import {
  ActivityApi,
  ActivityCategory,
  ActivityItem,
  ActivityLifecycle,
  ActivityQuery,
} from '@api/api-adapters/activity-api';
import { ActivityMonitor } from '@core/activity/activity-monitor';

type LoadStatus = 'loading' | 'success' | 'error';

@Injectable()
export class ActivityState {
  private readonly api = inject(ActivityApi);
  private readonly monitor = inject(ActivityMonitor);
  private readonly destroyRef = inject(DestroyRef);
  private readonly loadStatusState = signal<LoadStatus>('loading');
  private readonly loadedItems = signal<readonly ActivityItem[]>([]);
  private readonly nextCursorState = signal<string | undefined>(undefined);
  private readonly loadingMoreState = signal(false);
  private query: ActivityQuery = { limit: 20 };
  private request?: Subscription;

  readonly loadStatus = this.loadStatusState.asReadonly();
  readonly nextCursor = this.nextCursorState.asReadonly();
  readonly loadingMore = this.loadingMoreState.asReadonly();
  readonly paused = this.monitor.paused;
  readonly items = computed(() => {
    const liveItems = this.monitor.items();
    return this.loadedItems().map((item) => liveItems.get(item.reference) ?? item);
  });

  constructor() {
    this.destroyRef.onDestroy(() => this.request?.unsubscribe());
  }

  load(category?: ActivityCategory, lifecycle?: ActivityLifecycle): void {
    this.request?.unsubscribe();
    this.query = { category, lifecycle, limit: 20 };
    this.loadStatusState.set('loading');
    this.request = this.api
      .list(this.query)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.loadedItems.set(page.items);
          this.nextCursorState.set(page.nextCursor);
          this.monitor.register(page.items);
          this.loadStatusState.set('success');
        },
        error: () => this.loadStatusState.set('error'),
      });
  }

  loadMore(): void {
    const cursor = this.nextCursorState();
    if (!cursor || this.loadingMoreState()) return;
    this.loadingMoreState.set(true);
    this.request = this.api
      .list({ ...this.query, cursor })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          const existing = new Set(this.loadedItems().map((item) => item.reference));
          const appended = page.items.filter((item) => !existing.has(item.reference));
          this.loadedItems.update((items) => [...items, ...appended]);
          this.nextCursorState.set(page.nextCursor);
          this.monitor.register(page.items);
          this.loadingMoreState.set(false);
        },
        error: () => this.loadingMoreState.set(false),
      });
  }
}
