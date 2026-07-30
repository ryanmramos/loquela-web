import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { Api } from '@api/generated/api';
import { getDashboard } from '@api/generated/fn/dashboard-controller/get-dashboard';
import { DashboardDto } from '@api/generated/models/dashboard-dto';

export interface DashboardSnapshot {
  readonly generatedAt: string;
  readonly languagePair: {
    readonly sourceLanguage: string;
    readonly definitionLanguage: string;
  };
  readonly cards: {
    readonly status: DashboardSectionStatus;
    readonly cardsToStudy: number | null;
    readonly totalCards: number | null;
    readonly newCardsThisWeek: number | null;
  };
  readonly sources: {
    readonly status: DashboardSectionStatus;
    readonly totalSources: number | null;
    readonly readySources: number | null;
    readonly processingSources: number | null;
  };
  readonly storage: {
    readonly status: DashboardSectionStatus;
    readonly usedBytes: number | null;
  };
  readonly jobs: {
    readonly status: DashboardSectionStatus;
    readonly activeJobs: number | null;
  };
  readonly activity: {
    readonly status: DashboardSectionStatus;
    readonly items: readonly DashboardActivity[];
  };
}

export interface DashboardActivity {
  readonly kind: 'SOURCE' | 'JOB';
  readonly title: string;
  readonly detail: string;
  readonly status: string;
  readonly occurredAt: string;
  readonly target: string;
}

export type DashboardSectionStatus = 'AVAILABLE' | 'UNAVAILABLE';

@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private readonly api = inject(Api);

  get(): Observable<DashboardSnapshot> {
    return this.api.invoke(getDashboard).pipe(map(toDashboardSnapshot));
  }
}

function toDashboardSnapshot(response: DashboardDto): DashboardSnapshot {
  return {
    generatedAt: response.generatedAt,
    languagePair: {
      sourceLanguage: response.languagePair.sourceLanguage,
      definitionLanguage: response.languagePair.definitionLanguage,
    },
    cards: {
      status: response.cards.status,
      cardsToStudy: response.cards.cardsToStudy,
      totalCards: response.cards.totalCards,
      newCardsThisWeek: response.cards.newCardsThisWeek,
    },
    sources: {
      status: response.sources.status,
      totalSources: response.sources.totalSources,
      readySources: response.sources.readySources,
      processingSources: response.sources.processingSources,
    },
    storage: {
      status: response.storage.status,
      usedBytes: response.storage.usedBytes,
    },
    jobs: {
      status: response.jobs.status,
      activeJobs: response.jobs.activeJobs,
    },
    activity: {
      status: response.activity.status,
      items: response.activity.items.map((item) => ({ ...item })),
    },
  };
}
