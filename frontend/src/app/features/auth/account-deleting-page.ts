import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { switchMap, timer } from 'rxjs';

import { SessionFacade } from '@core/auth/session-facade';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { Alert } from '@app/shared/components/alert/alert';

@Component({
  selector: 'app-account-deleting-page',
  imports: [Alert, ButtonDirective],
  templateUrl: './account-deleting-page.html',
  styleUrl: './account-deleting-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountDeletingPage {
  private readonly facade = inject(SessionFacade);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly failed = signal(false);

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
            void this.router.navigate(['/login'], { replaceUrl: true });
          }
        },
        error: () => this.failed.set(true),
      });
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
