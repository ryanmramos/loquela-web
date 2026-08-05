import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { NEVER, of } from 'rxjs';

import { SettingsApi } from '@api/api-adapters/settings-api';
import { SessionFacade } from '@core/auth/session-facade';

import { AccountDeletingPage } from './account-deleting-page';

describe('AccountDeletingPage', () => {
  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('renders the safe current deletion state and opaque support code', () => {
    vi.useFakeTimers();
    const router = { navigate: vi.fn() };
    const fixture = createFixture(
      { refresh: vi.fn(() => of({ status: 'deleting' })), logout: vi.fn() },
      {
        currentDeletion: vi.fn(() =>
          of({
            status: 'NEEDS_ATTENTION',
            updatedAt: '2026-08-04T20:00:00Z',
            supportCode: 'opaque_support_code',
          }),
        ),
      },
      router,
    );

    vi.advanceTimersByTime(0);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Deletion needs attention');
    expect(element.textContent).toContain('opaque_support_code');
    expect(router.navigate).not.toHaveBeenCalled();
    fixture.destroy();
  });

  it('shows completion only after the session API recognizes ACCOUNT_DELETED', () => {
    vi.useFakeTimers();
    const router = { navigate: vi.fn() };
    const fixture = createFixture(
      {
        refresh: vi.fn(() => of({ status: 'anonymous', notice: 'account-deleted' })),
        logout: vi.fn(),
      },
      { currentDeletion: vi.fn(() => NEVER) },
      router,
    );

    vi.advanceTimersByTime(0);

    expect(router.navigate).toHaveBeenCalledWith(['/login'], {
      replaceUrl: true,
      state: { accountDeleted: true },
    });
    fixture.destroy();
  });
});

function createFixture(facade: object, settingsApi: object, router: object) {
  TestBed.configureTestingModule({
    imports: [AccountDeletingPage],
    providers: [
      { provide: SessionFacade, useValue: facade },
      { provide: SettingsApi, useValue: settingsApi },
      { provide: Router, useValue: router },
    ],
  });
  return TestBed.createComponent(AccountDeletingPage);
}
