import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AdminAccessApi, AdminAccessEntry } from '@api/api-adapters/admin-access-api';

import { AccessAdminPage } from './access-admin-page';

describe('AccessAdminPage', () => {
  const entry: AdminAccessEntry = {
    reference: 'access-1',
    email: 'admin@example.com',
    role: 'ADMIN',
    status: 'ACTIVE',
    version: 1,
    createdByUserId: null,
    updatedByUserId: null,
    createdAt: '2026-08-22T16:38:18Z',
    updatedAt: '2026-08-22T16:38:18Z',
  };
  const list = vi.fn(() =>
    of({ items: [entry], page: 0, pageSize: 20, totalItems: 1, totalPages: 1 }),
  );
  const create = vi.fn(() => of(entry));
  const update = vi.fn(() => of(entry));

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [AccessAdminPage],
      providers: [
        provideRouter([]),
        { provide: AdminAccessApi, useValue: { list, create, update } },
      ],
    });
  });

  it('loads the access policy and presents the responsive results structure', () => {
    const fixture = TestBed.createComponent(AccessAdminPage);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(list).toHaveBeenCalledWith({ page: 0, pageSize: 20 });
    expect(element.querySelector('[table]')).not.toBeNull();
    expect(element.querySelector('[cards]')).not.toBeNull();
    expect(element.textContent).toContain('1 account can sign in');
    expect(element.textContent).toContain('Search allowed accounts');
  });

  it('keeps empty filters quiet and exposes a clearable filtered state', () => {
    const page = TestBed.createComponent(AccessAdminPage).componentInstance;

    expect(page.hasActiveFilters()).toBe(false);
    page.filters.controls.search.setValue('missing@example.com');
    expect(page.hasActiveFilters()).toBe(true);
  });

  it('guards revocation behind an exact email confirmation', () => {
    const page = TestBed.createComponent(AccessAdminPage).componentInstance;

    page.toggleStatus(entry);
    expect(page.sensitiveAction()?.kind).toBe('revoke');
    expect(update).not.toHaveBeenCalled();

    page.confirmation.setValue(entry.email);
    page.confirmSensitive();

    expect(update).toHaveBeenCalledWith(entry, {
      role: 'ADMIN',
      status: 'REVOKED',
      confirmationEmail: entry.email,
    });
  });

  it('requires confirmation before changing an account role', () => {
    const page = TestBed.createComponent(AccessAdminPage).componentInstance;

    page.changeRole(entry);
    expect(page.sensitiveAction()?.kind).toBe('role');
    page.confirmation.setValue('different@example.com');
    page.confirmSensitive();
    expect(update).not.toHaveBeenCalled();

    page.confirmation.setValue(entry.email);
    page.confirmSensitive();
    expect(update).toHaveBeenCalledWith(entry, {
      role: 'USER',
      status: 'ACTIVE',
      confirmationEmail: entry.email,
    });
  });
});
