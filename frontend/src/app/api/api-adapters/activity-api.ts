import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { Api } from '@api/generated/api';
import {
  ListBrowserActivity$Params,
  listBrowserActivity,
} from '@api/generated/fn/browser-activity-controller/list-browser-activity';
import { queryBrowserActivityStatuses } from '@api/generated/fn/browser-activity-controller/query-browser-activity-statuses';
import { ActivityItemDto } from '@api/generated/models/activity-item-dto';

export type ActivityItem = Readonly<ActivityItemDto>;
export type ActivityCategory = ActivityItem['category'];
export type ActivityLifecycle = ActivityItem['lifecycle'];

export interface ActivityQuery {
  readonly category?: ActivityCategory;
  readonly lifecycle?: ActivityLifecycle;
  readonly cursor?: string;
  readonly limit?: number;
}

export interface ActivityPage {
  readonly items: readonly ActivityItem[];
  readonly nextCursor?: string;
  readonly activeCount: number;
}

export interface ActivityStatusResult {
  readonly items: readonly ActivityItem[];
  readonly activeCount: number;
}

@Injectable({ providedIn: 'root' })
export class ActivityApi {
  private readonly api = inject(Api);

  list(query: ActivityQuery = {}): Observable<ActivityPage> {
    return this.api.invoke(listBrowserActivity, query satisfies ListBrowserActivity$Params).pipe(
      map((response) => ({
        items: response.items.map((item) => ({ ...item })),
        nextCursor: response.nextCursor,
        activeCount: response.activeCount,
      })),
    );
  }

  statuses(references: readonly string[]): Observable<ActivityStatusResult> {
    return this.api
      .invoke(queryBrowserActivityStatuses, { body: { references: [...references] } })
      .pipe(
        map((response) => ({
          items: response.items.map((item) => ({ ...item })),
          activeCount: response.activeCount,
        })),
      );
  }
}
