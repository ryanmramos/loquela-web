import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { ActivityApi, ActivityItem } from '@api/api-adapters/activity-api';
import { ActivityMonitor } from '@core/activity/activity-monitor';

import { ActivityState } from './activity-state';

describe('ActivityState', () => {
  it('loads URL filters and appends an opaque cursor without duplicates', () => {
    const first = item('act_src_one');
    const second = item('act_lkp_two');
    const api = {
      list: vi
        .fn()
        .mockReturnValueOnce(of({ items: [first], nextCursor: 'opaque', activeCount: 1 }))
        .mockReturnValueOnce(of({ items: [first, second], activeCount: 1 })),
    };
    const monitor = { items: () => new Map(), paused: () => false, register: vi.fn() };
    const state = createState(api, monitor);

    state.load('SOURCE', 'ACTIVE');
    state.loadMore();

    expect(api.list).toHaveBeenNthCalledWith(1, {
      category: 'SOURCE',
      lifecycle: 'ACTIVE',
      limit: 20,
    });
    expect(api.list).toHaveBeenNthCalledWith(2, {
      category: 'SOURCE',
      lifecycle: 'ACTIVE',
      limit: 20,
      cursor: 'opaque',
    });
    expect(state.items().map((value) => value.reference)).toEqual(['act_src_one', 'act_lkp_two']);
  });

  it('overlays live status while preserving the server order', () => {
    const active = item('act_src_one');
    const completed = { ...active, lifecycle: 'COMPLETED' as const, status: 'READY' as const };
    const api = { list: vi.fn(() => of({ items: [active], activeCount: 1 })) };
    const monitor = {
      items: () => new Map([[completed.reference, completed]]),
      paused: () => false,
      register: vi.fn(),
    };
    const state = createState(api, monitor);

    state.load();

    expect(state.items()).toEqual([completed]);
  });
});

function createState(api: object, monitor: object): ActivityState {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      ActivityState,
      { provide: ActivityApi, useValue: api },
      { provide: ActivityMonitor, useValue: monitor },
    ],
  });
  return TestBed.inject(ActivityState);
}

function item(reference: string): ActivityItem {
  return {
    reference,
    category: 'SOURCE',
    operation: 'INGEST_SOURCE',
    lifecycle: 'ACTIVE',
    status: 'EXTRACTING',
    resourceLabel: 'Safe label',
    createdAt: '2026-08-04T20:00:00Z',
    updatedAt: '2026-08-04T20:01:00Z',
    target: '/app/sources/source_ref',
  };
}
