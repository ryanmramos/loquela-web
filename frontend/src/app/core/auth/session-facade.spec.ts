import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of, throwError } from 'rxjs';

import { BrowserSession, SessionApi } from '@api/api-adapters/session-api';
import { ApiProblemError } from '@core/errors/api-problem';

import { SessionFacade } from './session-facade';

const activeSession: BrowserSession = {
  userId: '529b7902-040a-4a40-8905-aa4ee06d2413',
  displayName: 'Session User',
  email: 'session@example.com',
  sourceLanguage: 'es',
  definitionLanguage: 'en',
  accountState: 'ACTIVE',
  linkedAccounts: { google: true, whatsapp: false },
};

describe('SessionFacade', () => {
  let api: {
    getCurrent: ReturnType<typeof vi.fn>;
    localLogin: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    api = {
      getCurrent: vi.fn(),
      localLogin: vi.fn(),
      logout: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [{ provide: SessionApi, useValue: api }],
    });
  });

  it('loads and shares the current session once', async () => {
    api.getCurrent.mockReturnValue(of(activeSession));
    const facade = TestBed.inject(SessionFacade);

    await expect(firstValueFrom(facade.ensureLoaded())).resolves.toMatchObject({
      status: 'authenticated',
    });
    await expect(firstValueFrom(facade.ensureLoaded())).resolves.toMatchObject({
      status: 'authenticated',
    });

    expect(api.getCurrent).toHaveBeenCalledTimes(1);
    expect(facade.view().user).toEqual(activeSession);
  });

  it('maps anonymous, deleting, and disabled session outcomes', async () => {
    api.getCurrent.mockReturnValueOnce(
      throwError(
        () =>
          new ApiProblemError({
            type: 'about:blank',
            title: 'Authentication required',
            status: 401,
            code: 'AUTHENTICATION_REQUIRED',
            correlationId: 'test',
          }),
      ),
    );
    let facade = TestBed.inject(SessionFacade);
    await expect(firstValueFrom(facade.ensureLoaded())).resolves.toMatchObject({
      status: 'anonymous',
    });

    TestBed.resetTestingModule();
    api.getCurrent.mockReturnValueOnce(of({ ...activeSession, accountState: 'DELETING' }));
    TestBed.configureTestingModule({ providers: [{ provide: SessionApi, useValue: api }] });
    facade = TestBed.inject(SessionFacade);
    await expect(firstValueFrom(facade.ensureLoaded())).resolves.toMatchObject({
      status: 'deleting',
    });

    TestBed.resetTestingModule();
    api.getCurrent.mockReturnValueOnce(
      throwError(
        () =>
          new ApiProblemError({
            type: 'about:blank',
            title: 'Account unavailable',
            status: 403,
            code: 'ACCOUNT_DISABLED',
            correlationId: 'test',
          }),
      ),
    );
    TestBed.configureTestingModule({ providers: [{ provide: SessionApi, useValue: api }] });
    facade = TestBed.inject(SessionFacade);
    await expect(firstValueFrom(facade.ensureLoaded())).resolves.toMatchObject({
      status: 'disabled',
      notice: 'account-disabled',
    });
  });

  it('sets local sessions and clears memory only after successful logout', async () => {
    api.localLogin.mockReturnValue(of(activeSession));
    api.logout.mockReturnValue(of(undefined));
    const facade = TestBed.inject(SessionFacade);

    await firstValueFrom(facade.loginLocally('learner'));
    expect(facade.view().status).toBe('authenticated');

    await firstValueFrom(facade.logout());
    expect(facade.view().status).toBe('anonymous');
  });
});
