import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { Api } from '@api/generated/api';
import { getBrowserSession } from '@api/generated/fn/browser-session-controller/get-browser-session';
import { logoutBrowserSession } from '@api/generated/fn/browser-session-controller/logout-browser-session';
import { BrowserSessionDto } from '@api/generated/models/browser-session-dto';

export interface BrowserSession {
  readonly userId: string;
  readonly displayName: string | null;
  readonly email: string | null;
  readonly sourceLanguage: string;
  readonly definitionLanguage: string;
  readonly interfaceLanguage: 'en' | 'es' | null;
  readonly accountState: 'ACTIVE' | 'DELETING';
  readonly linkedAccounts: {
    readonly google: boolean;
    readonly whatsapp: boolean;
  };
}

/**
 * Stable application-facing boundary around the generated transport client.
 * Feature code consumes this adapter rather than depending on generator-specific APIs.
 */
@Injectable({ providedIn: 'root' })
export class SessionApi {
  private readonly api = inject(Api);
  private readonly http = inject(HttpClient);

  getCurrent(): Observable<BrowserSession> {
    return this.api.invoke(getBrowserSession).pipe(map(toBrowserSession));
  }

  updateInterfaceLanguage(interfaceLanguage: 'en' | 'es'): Observable<BrowserSession> {
    return this.http
      .patch<BrowserSessionDto>('/api/v1/session/interface-language', { interfaceLanguage })
      .pipe(map(toBrowserSession));
  }

  logout(): Observable<void> {
    return this.api.invoke(logoutBrowserSession).pipe(map(() => undefined));
  }

  localLogin(accountId: LocalAccountId): Observable<BrowserSession> {
    // This endpoint is deliberately excluded from the production OpenAPI contract.
    return this.http
      .post<BrowserSessionDto>('/api/v1/auth/local-login', { accountId })
      .pipe(map(toBrowserSession));
  }
}

export type LocalAccountId = 'learner' | 'reviewer';

function toBrowserSession(response: BrowserSessionDto): BrowserSession {
  return {
    userId: response.userId,
    displayName: response.displayName ?? null,
    email: response.email ?? null,
    sourceLanguage: response.sourceLanguage,
    definitionLanguage: response.definitionLanguage,
    interfaceLanguage: response.interfaceLanguage ?? null,
    accountState: response.accountState,
    linkedAccounts: {
      google: response.linkedAccounts.google,
      whatsapp: response.linkedAccounts.whatsapp,
    },
  };
}
