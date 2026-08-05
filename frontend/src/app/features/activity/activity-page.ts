import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';

import { ActivityCategory, ActivityItem, ActivityLifecycle } from '@api/api-adapters/activity-api';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { StatusBadge, StatusTone } from '@app/shared/components/status-badge/status-badge';
import { UiState } from '@app/shared/components/ui-state/ui-state';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { TranslationService } from '@core/i18n/translation.service';

import { ActivityState } from './activity-state';

@Component({
  selector: 'app-activity-page',
  imports: [ButtonDirective, StatusBadge, TranslatePipe, UiState],
  providers: [ActivityState],
  templateUrl: './activity-page.html',
  styleUrl: './activity-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActivityPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly translations = inject(TranslationService);
  readonly state = inject(ActivityState);

  lifecycle = '';
  category = '';

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.lifecycle = this.validLifecycle(params.get('lifecycle') ?? params.get('state'));
      this.category = this.validCategory(params.get('category'));
      this.state.load(
        this.category ? (this.category as ActivityCategory) : undefined,
        this.lifecycle ? (this.lifecycle as ActivityLifecycle) : undefined,
      );
    });
  }

  setLifecycle(lifecycle: string): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { lifecycle: lifecycle || null, state: null },
      queryParamsHandling: 'merge',
    });
  }

  setCategory(category: string): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { category: category || null },
      queryParamsHandling: 'merge',
    });
  }

  filterLabel(lifecycle: string): string {
    return this.translations.translate(`activity.filter.${lifecycle || 'ALL'}` as never);
  }

  retry(): void {
    this.state.load(
      this.category ? (this.category as ActivityCategory) : undefined,
      this.lifecycle ? (this.lifecycle as ActivityLifecycle) : undefined,
    );
  }

  title(item: ActivityItem): string {
    if (item.resourceLabel) return item.resourceLabel;
    return this.translations.translate(`activity.operation.${item.operation}` as never);
  }

  detail(item: ActivityItem): string {
    const status = this.translations.translate(`activity.status.${item.status}` as never);
    if (!item.deliveryStatus) return status;
    return `${status} · ${this.translations.translate(`activity.delivery.${item.deliveryStatus}` as never)}`;
  }

  categoryLabel(category: ActivityCategory): string {
    return this.translations.translate(`activity.category.${category}` as never);
  }

  lifecycleLabel(lifecycle: ActivityLifecycle): string {
    return this.translations.translate(`activity.lifecycle.${lifecycle}` as never);
  }

  lifecycleTone(lifecycle: ActivityLifecycle): StatusTone {
    if (lifecycle === 'COMPLETED') return 'success';
    if (lifecycle === 'FAILED') return 'danger';
    return 'info';
  }

  icon(category: ActivityCategory): string {
    return category === 'SOURCE' ? 'T' : category === 'LOOKUP' ? '⌕' : '⇩';
  }

  formatDate(value: string): string {
    return new Intl.DateTimeFormat(this.translations.language(), {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(value));
  }

  private validLifecycle(value: string | null): string {
    const normalized = value?.toUpperCase();
    return normalized && ['ACTIVE', 'COMPLETED', 'FAILED'].includes(normalized) ? normalized : '';
  }

  private validCategory(value: string | null): string {
    const normalized = value?.toUpperCase();
    return normalized && ['SOURCE', 'LOOKUP', 'EXPORT'].includes(normalized) ? normalized : '';
  }
}
