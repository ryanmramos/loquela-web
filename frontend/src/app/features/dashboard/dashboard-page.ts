import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SessionFacade } from '@core/auth/session-facade';
import { Alert } from '@app/shared/components/alert/alert';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { Skeleton } from '@app/shared/components/skeleton/skeleton';
import { StatusBadge, StatusTone } from '@app/shared/components/status-badge/status-badge';
import { UiState } from '@app/shared/components/ui-state/ui-state';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { TranslationService } from '@core/i18n/translation.service';
import { ActivityItem } from '@api/api-adapters/activity-api';

import { DashboardState } from './dashboard-state';

@Component({
  selector: 'app-dashboard-page',
  imports: [
    Alert,
    ButtonDirective,
    DecimalPipe,
    RouterLink,
    Skeleton,
    StatusBadge,
    UiState,
    TranslatePipe,
  ],
  providers: [DashboardState],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  private readonly sessionFacade = inject(SessionFacade);
  private readonly translations = inject(TranslationService);

  readonly state = inject(DashboardState);
  readonly session = this.sessionFacade.view;

  constructor() {
    this.state.load();
  }

  submitCapture(event: SubmitEvent, input: HTMLTextAreaElement): void {
    event.preventDefault();
    this.state.capture(input.value);
  }

  languageName(tag: string): string {
    try {
      return (
        new Intl.DisplayNames([document.documentElement.lang || navigator.language], {
          type: 'language',
        }).of(tag) ?? tag
      );
    } catch {
      return tag;
    }
  }

  formatStorage(bytes: number): string {
    if (bytes < 1_000) {
      return `${bytes} B`;
    }

    const units = ['KB', 'MB', 'GB', 'TB'];
    let value = bytes / 1_000;
    let unitIndex = 0;
    while (value >= 1_000 && unitIndex < units.length - 1) {
      value /= 1_000;
      unitIndex += 1;
    }
    const formatted = new Intl.NumberFormat(document.documentElement.lang || navigator.language, {
      maximumFractionDigits: value < 10 ? 1 : 0,
    }).format(value);
    return `${formatted} ${units[unitIndex]}`;
  }

  formatHeadingDate(value?: string): string {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    return new Intl.DateTimeFormat(this.translations.language(), {
      weekday: 'long',
      month: 'long',
      day: 'numeric',
    }).format(date);
  }

  formatActivityDate(value?: string): string {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    return new Intl.DateTimeFormat(this.translations.language(), {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(date);
  }

  statusTone(lifecycle: ActivityItem['lifecycle']): StatusTone {
    if (lifecycle === 'COMPLETED') {
      return 'success';
    }
    if (lifecycle === 'FAILED') {
      return 'danger';
    }
    return 'info';
  }

  activityTitle(item: ActivityItem): string {
    return (
      item.resourceLabel ??
      this.translations.translate(`activity.operation.${item.operation}` as never)
    );
  }

  activityDetail(item: ActivityItem): string {
    const status = this.translations.translate(`activity.status.${item.status}` as never);
    return item.deliveryStatus
      ? `${status} · ${this.translations.translate(`activity.delivery.${item.deliveryStatus}` as never)}`
      : status;
  }

  activityStatus(lifecycle: ActivityItem['lifecycle']): string {
    return this.translations.translate(`activity.lifecycle.${lifecycle}` as never);
  }
}
