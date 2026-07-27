import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SessionFacade } from '@core/auth/session-facade';
import { Alert } from '@app/shared/components/alert/alert';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { Skeleton } from '@app/shared/components/skeleton/skeleton';
import { StatusBadge, StatusTone } from '@app/shared/components/status-badge/status-badge';
import { UiState } from '@app/shared/components/ui-state/ui-state';

import { DashboardState } from './dashboard-state';

@Component({
  selector: 'app-dashboard-page',
  imports: [
    Alert,
    ButtonDirective,
    DatePipe,
    DecimalPipe,
    RouterLink,
    Skeleton,
    StatusBadge,
    UiState,
  ],
  providers: [DashboardState],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  private readonly sessionFacade = inject(SessionFacade);

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
      return new Intl.DisplayNames(['en'], { type: 'language' }).of(tag) ?? tag;
    } catch {
      return tag;
    }
  }

  statusTone(status: string): StatusTone {
    if (status === 'READY' || status === 'SUCCEEDED') {
      return 'success';
    }
    if (status === 'FAILED' || status === 'DEAD') {
      return 'danger';
    }
    if (status === 'RETRY_WAIT') {
      return 'warning';
    }
    return 'info';
  }
}
