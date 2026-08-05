import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import { ActivityApi, ActivityItem } from '@api/api-adapters/activity-api';
import { ShellDashboardStore } from '@core/layout/shell-dashboard-store';
import { TranslationService } from '@core/i18n/translation.service';
import { ToastService } from '@app/shared/components/toast/toast.service';

import { ActivityMonitor } from './activity-monitor';

describe('ActivityMonitor', () => {
  const originalVisibility = document.visibilityState;
  const originalOnline = navigator.onLine;

  beforeEach(() => {
    vi.useFakeTimers();
    setAvailability('visible', true);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
    setAvailability(originalVisibility, originalOnline);
    TestBed.resetTestingModule();
  });

  it('keeps the initial baseline silent and coalesces terminal transitions without content', () => {
    const active = item();
    const api = {
      list: vi.fn(() => of({ items: [active], activeCount: 1 })),
      statuses: vi.fn(() =>
        of({
          items: [
            { ...active, lifecycle: 'COMPLETED', status: 'READY' },
            { ...item('act_lkp_two'), lifecycle: 'FAILED', status: 'FAILED' },
          ],
          activeCount: 0,
        }),
      ),
    };
    const toasts = { show: vi.fn() };
    const monitor = createMonitor(api, toasts);

    monitor.start();
    expect(toasts.show).not.toHaveBeenCalled();
    monitor.register([item('act_lkp_two')]);
    vi.runOnlyPendingTimers();

    expect(toasts.show).toHaveBeenCalledOnce();
    const announcement = toasts.show.mock.calls[0].join(' ');
    expect(announcement).toContain('activity.completedCount');
    expect(announcement).toContain('activity.failedCount');
    expect(announcement).not.toContain('Private source name');
    expect(vi.getTimerCount()).toBe(0);
  });

  it('pauses while hidden and refreshes immediately on return', () => {
    const api = {
      list: vi.fn(() => of({ items: [item()], activeCount: 1 })),
      statuses: vi.fn(() => of({ items: [item()], activeCount: 1 })),
    };
    const monitor = createMonitor(api);
    setAvailability('hidden', true);
    monitor.start();
    expect(api.list).not.toHaveBeenCalled();

    setAvailability('visible', true);
    document.dispatchEvent(new Event('visibilitychange'));
    expect(api.list).toHaveBeenCalledOnce();

    setAvailability('visible', false);
    window.dispatchEvent(new Event('offline'));
    expect(monitor.paused()).toBe(true);
    expect(vi.getTimerCount()).toBe(0);
    setAvailability('visible', true);
    window.dispatchEvent(new Event('online'));
    expect(api.statuses).toHaveBeenCalledOnce();
  });

  it('honors Retry-After and removes every timer on shell teardown', () => {
    vi.spyOn(Math, 'random').mockReturnValue(0.5);
    const active = item();
    const api = {
      list: vi.fn(() => of({ items: [active], activeCount: 1 })),
      statuses: vi.fn(() =>
        throwError(
          () =>
            new HttpErrorResponse({
              status: 429,
              headers: new HttpHeaders({ 'Retry-After': '10' }),
            }),
        ),
      ),
    };
    const monitor = createMonitor(api);
    monitor.start();
    vi.advanceTimersByTime(2_000);
    expect(api.statuses).toHaveBeenCalledOnce();
    vi.advanceTimersByTime(9_999);
    expect(api.statuses).toHaveBeenCalledOnce();
    monitor.stop();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('does not update state after teardown when a request completes late', () => {
    const response = new Subject<{ items: readonly ActivityItem[]; activeCount: number }>();
    const api = { list: vi.fn(() => response), statuses: vi.fn() };
    const store = { updateActivity: vi.fn() };
    const monitor = createMonitor(api, undefined, store);
    monitor.start();
    monitor.stop();
    response.next({ items: [item()], activeCount: 1 });
    expect(store.updateActivity).toHaveBeenLastCalledWith(null);
  });
});

function createMonitor(
  api: object,
  toasts: object = { show: vi.fn() },
  store: object = { updateActivity: vi.fn() },
): ActivityMonitor {
  TestBed.configureTestingModule({
    providers: [
      { provide: ActivityApi, useValue: api },
      { provide: ToastService, useValue: toasts },
      { provide: ShellDashboardStore, useValue: store },
      {
        provide: TranslationService,
        useValue: {
          translate: (key: string, values?: { count?: number }) => `${key}:${values?.count ?? ''}`,
        },
      },
    ],
  });
  return TestBed.inject(ActivityMonitor);
}

function setAvailability(visibility: DocumentVisibilityState, online: boolean): void {
  Object.defineProperty(document, 'visibilityState', { configurable: true, value: visibility });
  Object.defineProperty(navigator, 'onLine', { configurable: true, value: online });
}

function item(reference = 'act_src_one'): ActivityItem {
  return {
    reference,
    category: reference.includes('lkp') ? 'LOOKUP' : 'SOURCE',
    operation: reference.includes('lkp') ? 'LOOKUP' : 'INGEST_SOURCE',
    lifecycle: 'ACTIVE',
    status: reference.includes('lkp') ? 'PENDING' : 'EXTRACTING',
    resourceLabel: 'Private source name',
    createdAt: '2026-08-04T20:00:00Z',
    updatedAt: '2026-08-04T20:01:00Z',
  };
}
