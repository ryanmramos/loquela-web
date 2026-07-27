import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';

import { LocalAccountId } from '@api/api-adapters/session-api';
import { RUNTIME_CONFIG } from '@core/config/runtime-config';
import { safeAuthenticatedReturnUrl } from '@core/auth/return-url';
import { SessionFacade } from '@core/auth/session-facade';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { Alert } from '@app/shared/components/alert/alert';

@Component({
  selector: 'app-login-page',
  imports: [Alert, ButtonDirective],
  templateUrl: './login-page.html',
  styleUrl: './login-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly facade = inject(SessionFacade);
  private readonly destroyRef = inject(DestroyRef);
  private readonly config = inject(RUNTIME_CONFIG);

  readonly localLoginEnabled = this.config.localLoginEnabled;
  readonly pendingAccount = signal<LocalAccountId | null>(null);
  readonly loginFailed = signal(false);
  readonly reason = this.route.snapshot.queryParamMap.get('reason');
  readonly returnUrl = safeAuthenticatedReturnUrl(
    this.route.snapshot.queryParamMap.get('returnUrl'),
  );
  readonly googleLoginUrl = `${this.config.googleLoginPath}?returnTo=${encodeURIComponent(
    this.returnUrl,
  )}`;

  loginLocally(accountId: LocalAccountId): void {
    if (this.pendingAccount()) {
      return;
    }
    this.loginFailed.set(false);
    this.pendingAccount.set(accountId);
    this.facade
      .loginLocally(accountId)
      .pipe(
        finalize(() => this.pendingAccount.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          void this.router.navigateByUrl(this.returnUrl);
        },
        error: () => this.loginFailed.set(true),
      });
  }
}
