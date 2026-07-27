import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';

import { SessionFacade } from '@core/auth/session-facade';
import { AppShell } from '@core/layout/app-shell';
import { Alert } from '@app/shared/components/alert/alert';

@Component({
  selector: 'app-dashboard-page',
  imports: [Alert, AppShell],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  private readonly facade = inject(SessionFacade);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly session = this.facade.view;
  readonly logoutFailed = signal(false);

  handleUserAction(action: string): void {
    if (action !== 'sign-out') {
      return;
    }
    this.logoutFailed.set(false);
    this.facade
      .logout()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          void this.router.navigate(['/login'], { replaceUrl: true });
        },
        error: () => this.logoutFailed.set(true),
      });
  }
}
