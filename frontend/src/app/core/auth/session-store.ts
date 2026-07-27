import { Injectable, signal } from '@angular/core';

import { BrowserSession } from '@api/api-adapters/session-api';

export type SessionStatus = 'unknown' | 'anonymous' | 'authenticated' | 'deleting' | 'disabled';

export type SessionNotice = 'session-expired' | 'account-disabled';

export interface SessionView {
  readonly status: SessionStatus;
  readonly user?: BrowserSession;
  readonly notice?: SessionNotice;
}

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

  setAnonymous(notice?: SessionNotice): void {
    this.mutableView.set({
      status: notice === 'account-disabled' ? 'disabled' : 'anonymous',
      ...(notice ? { notice } : {}),
    });
  }
}
