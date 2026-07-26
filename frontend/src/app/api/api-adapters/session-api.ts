import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { Api } from '@api/generated/api';
import { getBrowserSession } from '@api/generated/fn/browser-session-controller/get-browser-session';
import { BrowserSessionDto } from '@api/generated/models/browser-session-dto';

export interface BrowserSession {
  readonly userId: string;
  readonly displayName: string | null;
  readonly email: string | null;
  readonly sourceLanguage: string;
  readonly definitionLanguage: string;
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

  getCurrent(): Observable<BrowserSession> {
    return this.api.invoke(getBrowserSession).pipe(map(toBrowserSession));
  }
}

function toBrowserSession(response: BrowserSessionDto): BrowserSession {
  return {
    userId: response.userId,
    displayName: response.displayName ?? null,
    email: response.email ?? null,
    sourceLanguage: response.sourceLanguage,
    definitionLanguage: response.definitionLanguage,
    linkedAccounts: {
      google: response.linkedAccounts.google,
      whatsapp: response.linkedAccounts.whatsapp,
    },
  };
}
