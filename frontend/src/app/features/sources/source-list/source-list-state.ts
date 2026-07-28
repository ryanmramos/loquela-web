import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';

import { SourceApi, SourceListQuery, SourcePage } from '@api/api-adapters/source-api';

type LoadStatus = 'loading' | 'success' | 'error';

@Injectable()
export class SourceListState {
  private readonly sourceApi = inject(SourceApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly loadStatusState = signal<LoadStatus>('loading');
  private readonly pageState = signal<SourcePage | null>(null);
  private request?: Subscription;

  readonly loadStatus = this.loadStatusState.asReadonly();
  readonly page = this.pageState.asReadonly();

  constructor() {
    this.destroyRef.onDestroy(() => this.request?.unsubscribe());
  }

  load(query: SourceListQuery): void {
    this.request?.unsubscribe();
    this.loadStatusState.set('loading');
    this.request = this.sourceApi
      .list(query)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.pageState.set(page);
          this.loadStatusState.set('success');
        },
        error: () => this.loadStatusState.set('error'),
      });
  }
}
