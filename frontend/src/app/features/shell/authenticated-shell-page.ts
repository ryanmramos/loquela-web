import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterOutlet } from '@angular/router';
import { ConfirmDialog } from 'primeng/confirmdialog';

import { SessionFacade } from '@core/auth/session-facade';
import { AppShell } from '@core/layout/app-shell';
import { ShellDashboardStore } from '@core/layout/shell-dashboard-store';
import { Alert } from '@app/shared/components/alert/alert';
import { ActivityMonitor } from '@core/activity/activity-monitor';

@Component({
  selector: 'app-authenticated-shell-page',
  imports: [Alert, AppShell, ConfirmDialog, RouterOutlet],
  template: `
    @if (session().user; as user) {
      <app-shell
        [userName]="user.displayName || 'Loquela learner'"
        [userEmail]="user.email || 'Google account'"
        [activeJobCount]="shellDashboard.activeActivity()"
        [cardsToStudy]="shellDashboard.cardsToStudy()"
        [isAdmin]="user.roles.includes('ADMIN')"
        (searchSubmitted)="openLookup($event)"
        (userActionSelected)="handleUserAction($event)"
      >
        @if (logoutFailed) {
          <div class="shell-alert">
            <app-alert title="Sign out failed" tone="danger">
              Your session is still active. Try signing out again.
            </app-alert>
          </div>
        }
        <router-outlet />
      </app-shell>
      <p-confirm-dialog />
    }
  `,
  styles: `
    .shell-alert {
      padding: var(--space-4) var(--space-10) 0;
    }

    @media (max-width: 40rem) {
      .shell-alert {
        padding-inline: var(--space-4);
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthenticatedShellPage {
  private readonly facade = inject(SessionFacade);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly activityMonitor = inject(ActivityMonitor);

  readonly shellDashboard = inject(ShellDashboardStore);
  readonly session = this.facade.view;
  logoutFailed = false;

  constructor() {
    // Counts belong to the current authenticated user and must never survive a user change.
    this.shellDashboard.reset();
    this.activityMonitor.start();
    this.destroyRef.onDestroy(() => this.activityMonitor.stop());
  }

  openLookup(query: string): void {
    void this.router.navigate(['/app/lookup'], { queryParams: { query } });
  }

  handleUserAction(action: string): void {
    if (action === 'profile') {
      void this.router.navigate(['/app/settings']);
      return;
    }
    if (action !== 'sign-out') {
      return;
    }

    this.logoutFailed = false;
    this.facade
      .logout()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.shellDashboard.reset();
          this.activityMonitor.stop();
          void this.router.navigate(['/login'], { replaceUrl: true });
        },
        error: () => {
          this.logoutFailed = true;
        },
      });
  }
}
