import { Injectable, signal } from '@angular/core';

import { BrowserSession } from '@api/api-adapters/session-api';

export type SessionStatus = 'unknown' | 'anonymous' | 'authenticated' | 'deleting' | 'disabled';

export type SessionNotice = 'session-expired' | 'account-disabled' | 'account-deleted';

/**
 * A session either has an authenticated user or it does not. Modeling those cases separately
 * keeps templates from handling combinations such as an anonymous session with a user.
 */
export type SessionView =
  | { readonly status: 'unknown'; readonly user?: never }
  | {
      readonly status: 'anonymous';
      readonly user?: never;
      readonly notice?: Exclude<SessionNotice, 'account-disabled'>;
    }
  | {
      readonly status: 'disabled';
      readonly user?: never;
      readonly notice: Extract<SessionNotice, 'account-disabled'>;
    }
  | { readonly status: 'authenticated'; readonly user: BrowserSession }
  | { readonly status: 'deleting'; readonly user: BrowserSession };

@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly mutableView = signal<SessionView>({ status: 'unknown' });

  readonly view = this.mutableView.asReadonly();

  setAuthenticated(user: BrowserSession): void {
    this.mutableView.set({
      status: user.accountState === 'DELETING' ? 'deleting' : 'authenticated',
      user,
    });
  }

  setAnonymous(notice?: Exclude<SessionNotice, 'account-disabled'>): void {
    this.mutableView.set({
      status: 'anonymous',
      ...(notice ? { notice } : {}),
    });
  }

  setDisabled(): void {
    this.mutableView.set({ status: 'disabled', notice: 'account-disabled' });
  }

  markDeleting(): void {
    const current = this.mutableView();
    if (current.status !== 'authenticated' && current.status !== 'deleting') return;
    this.mutableView.set({
      status: 'deleting',
      user: { ...current.user, accountState: 'DELETING' },
    });
  }
}
