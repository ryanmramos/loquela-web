import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { Select } from 'primeng/select';

import { ActivityCategory, ActivityItem, ActivityLifecycle } from '@api/api-adapters/activity-api';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { StatusBadge, StatusTone } from '@app/shared/components/status-badge/status-badge';
import { UiState } from '@app/shared/components/ui-state/ui-state';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { TranslationService } from '@core/i18n/translation.service';
import { AppIcon, AppIconName } from '@app/shared/components/icon/app-icon';

import { ActivityState } from './activity-state';

@Component({
  selector: 'app-activity-page',
  imports: [AppIcon, ButtonDirective, FormsModule, Select, StatusBadge, TranslatePipe, UiState],
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

  lifecycle: ActivityLifecycle | '' = '';
  category: ActivityCategory | '' = '';
  readonly categoryOptions = computed(() => [
    { value: '', label: this.translations.translate('activity.category.ALL') },
    { value: 'SOURCE', label: this.translations.translate('activity.category.SOURCE') },
    { value: 'LOOKUP', label: this.translations.translate('activity.category.LOOKUP') },
    { value: 'EXPORT', label: this.translations.translate('activity.category.EXPORT') },
  ]);

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.lifecycle = this.validLifecycle(params.get('lifecycle') ?? params.get('state'));
      this.category = this.validCategory(params.get('category'));
      this.state.load(this.category || undefined, this.lifecycle || undefined);
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
    return this.translations.translateDynamic(
      `activity.filter.${lifecycle || 'ALL'}`,
      'activity.filter.ALL',
    );
  }

  retry(): void {
    this.state.load(this.category || undefined, this.lifecycle || undefined);
  }

  title(item: ActivityItem): string {
    if (item.resourceLabel) return item.resourceLabel;
    return this.translations.translateDynamic(
      `activity.operation.${item.operation}`,
      'activity.operation.LOOKUP',
    );
  }

  detail(item: ActivityItem): string {
    const status = this.translations.translateDynamic(
      `activity.status.${item.status}`,
      'activity.status.FAILED',
    );
    if (!item.deliveryStatus) return status;
    return `${status} · ${this.translations.translateDynamic(
      `activity.delivery.${item.deliveryStatus}`,
      'activity.delivery.NOT_APPLICABLE',
    )}`;
  }

  categoryLabel(category: ActivityCategory): string {
    return this.translations.translateDynamic(
      `activity.category.${category}`,
      'activity.category.LOOKUP',
    );
  }

  lifecycleLabel(lifecycle: ActivityLifecycle): string {
    return this.translations.translateDynamic(
      `activity.lifecycle.${lifecycle}`,
      'activity.lifecycle.ACTIVE',
    );
  }

  lifecycleTone(lifecycle: ActivityLifecycle): StatusTone {
    if (lifecycle === 'COMPLETED') return 'success';
    if (lifecycle === 'FAILED') return 'danger';
    return 'info';
  }

  icon(category: ActivityCategory): AppIconName {
    return category === 'SOURCE' ? 'document' : category === 'LOOKUP' ? 'search' : 'download';
  }

  formatDate(value: string): string {
    return new Intl.DateTimeFormat(this.translations.language(), {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(value));
  }

  private validLifecycle(value: string | null): ActivityLifecycle | '' {
    const normalized = value?.toUpperCase();
    return normalized === 'ACTIVE' || normalized === 'COMPLETED' || normalized === 'FAILED'
      ? normalized
      : '';
  }

  private validCategory(value: string | null): ActivityCategory | '' {
    const normalized = value?.toUpperCase();
    return normalized === 'SOURCE' || normalized === 'LOOKUP' || normalized === 'EXPORT'
      ? normalized
      : '';
  }
}
