import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, of, shareReplay, tap, throwError } from 'rxjs';

import { LocalAccountId, SessionApi } from '@api/api-adapters/session-api';
import { ApiProblemError } from '@core/errors/api-problem';

import { SessionStore, SessionView } from './session-store';
import { InterfaceLanguage, TranslationService } from '@core/i18n/translation.service';

@Injectable({ providedIn: 'root' })
export class SessionFacade {
  private readonly api = inject(SessionApi);
  private readonly store = inject(SessionStore);
  private readonly translations = inject(TranslationService);
  private loadRequest?: Observable<SessionView>;

  readonly view = this.store.view;

  ensureLoaded(): Observable<SessionView> {
    if (this.view().status !== 'unknown') {
      return of(this.view());
    }

    this.loadRequest ??= this.api.getCurrent().pipe(
      tap((session) => {
        this.translations.setLanguage(session.interfaceLanguage ?? this.translations.language());
        this.store.setAuthenticated(session);
      }),
      map(() => this.view()),
      catchError((error: unknown) => {
        if (error instanceof ApiProblemError && error.problem.status === 401) {
          this.store.setAnonymous();
          return of(this.view());
        }
        if (
          error instanceof ApiProblemError &&
          error.problem.status === 403 &&
          error.problem.code === 'ACCOUNT_DISABLED'
        ) {
          this.store.setAnonymous('account-disabled');
          return of(this.view());
        }
        return throwError(() => error);
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.loadRequest;
  }

  refresh(): Observable<SessionView> {
    return this.api.getCurrent().pipe(
      tap((session) => this.store.setAuthenticated(session)),
      map(() => this.view()),
      catchError((error: unknown) => {
        if (error instanceof ApiProblemError && error.problem.status === 401) {
          this.store.setAnonymous();
          return of(this.view());
        }
        if (
          error instanceof ApiProblemError &&
          error.problem.status === 403 &&
          (error.problem.code === 'ACCOUNT_DISABLED' || error.problem.code === 'ACCOUNT_DELETED')
        ) {
          this.store.setAnonymous(
            error.problem.code === 'ACCOUNT_DISABLED' ? 'account-disabled' : undefined,
          );
          return of(this.view());
        }
        return throwError(() => error);
      }),
    );
  }

  loginLocally(accountId: LocalAccountId): Observable<SessionView> {
    return this.api.localLogin(accountId).pipe(
      tap((session) => this.store.setAuthenticated(session)),
      map(() => this.view()),
    );
  }

  logout(): Observable<void> {
    return this.api.logout().pipe(tap(() => this.store.setAnonymous()));
  }

  updateInterfaceLanguage(language: InterfaceLanguage): Observable<void> {
    this.translations.setLanguage(language);
    return this.api.updateInterfaceLanguage(language).pipe(
      tap((session) => this.store.setAuthenticated(session)),
      map(() => undefined),
    );
  }

  markDeleting(): void {
    this.store.markDeleting();
  }
}
