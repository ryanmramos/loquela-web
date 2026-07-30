import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Api } from '@api/generated/api';
import { createPracticeSession } from '@api/generated/fn/browser-practice-controller/create-practice-session';
import { endPracticeSession } from '@api/generated/fn/browser-practice-controller/end-practice-session';
import { getActivePracticeSession } from '@api/generated/fn/browser-practice-controller/get-active-practice-session';
import { getPracticeSession } from '@api/generated/fn/browser-practice-controller/get-practice-session';
import { ratePracticeCard } from '@api/generated/fn/browser-practice-controller/rate-practice-card';
import { revealPracticeAnswer } from '@api/generated/fn/browser-practice-controller/reveal-practice-answer';
import { PracticeRatingRequestDto, PracticeSessionDto } from '@api/generated/models';

export type PracticeSession = PracticeSessionDto;
export type PracticeRating = PracticeRatingRequestDto['rating'];

@Injectable({ providedIn: 'root' })
export class PracticeApi {
  private readonly api = inject(Api);

  active(): Observable<PracticeSession | null> {
    // Spring represents "no active session" as 204, whose response body is null.
    return this.api.invoke(getActivePracticeSession);
  }

  start(deckReference: string): Observable<PracticeSession> {
    return this.api.invoke(createPracticeSession, { body: { deckReference } });
  }

  get(reference: string): Observable<PracticeSession> {
    return this.api.invoke(getPracticeSession, { reference });
  }

  reveal(reference: string): Observable<PracticeSession> {
    return this.api.invoke(revealPracticeAnswer, { reference });
  }

  rate(reference: string, requestId: string, rating: PracticeRating): Observable<PracticeSession> {
    return this.api.invoke(ratePracticeCard, { reference, body: { requestId, rating } });
  }

  end(reference: string): Observable<PracticeSession> {
    return this.api.invoke(endPracticeSession, { reference });
  }
}
