import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { switchMap, timer } from 'rxjs';
import { AccountDeletionStatus, SettingsApi } from '@api/api-adapters/settings-api';

import { SessionFacade } from '@core/auth/session-facade';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { Alert } from '@app/shared/components/alert/alert';

@Component({
  selector: 'app-account-deleting-page',
  imports: [Alert, ButtonDirective, DatePipe],
  templateUrl: './account-deleting-page.html',
  styleUrl: './account-deleting-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountDeletingPage {
  private readonly facade = inject(SessionFacade);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly settingsApi = inject(SettingsApi);

  readonly failed = signal(false);
  readonly deletion = signal<AccountDeletionStatus | null>(null);

  constructor() {
    // Cleanup runs outside the browser session. Poll one bounded resource so a
    // completed deletion also clears stale client authentication after refresh.
    timer(0, 5_000)
      .pipe(
        switchMap(() => this.facade.refresh()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (session) => {
          if (session.status !== 'deleting') {
            void this.router.navigate(['/login'], {
              replaceUrl: true,
              ...(session.status === 'anonymous' && session.notice === 'account-deleted'
                ? { state: { accountDeleted: true } }
                : {}),
            });
          }
        },
        error: () => this.failed.set(true),
      });

    timer(0, 5_000)
      .pipe(
        switchMap(() => this.settingsApi.currentDeletion()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (deletion) => {
          this.deletion.set(deletion);
          this.failed.set(false);
        },
        error: () => this.failed.set(true),
      });
  }

  statusLabel(status: AccountDeletionStatus['status']): string {
    return status === 'QUEUED'
      ? 'Deletion is queued'
      : status === 'IN_PROGRESS'
        ? 'Deletion is in progress'
        : 'Deletion needs attention';
  }

  signOut(): void {
    this.failed.set(false);
    this.facade
      .logout()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          void this.router.navigate(['/login'], { replaceUrl: true });
        },
        error: () => this.failed.set(true),
      });
  }
}
