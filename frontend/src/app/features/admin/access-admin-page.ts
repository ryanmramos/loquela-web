import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Table } from 'primeng/table';

import {
  AccessRole,
  AccessStatus,
  AdminAccessApi,
  AdminAccessEntry,
  AdminAccessEntryPage,
} from '@api/api-adapters/admin-access-api';
import { Alert } from '@app/shared/components/alert/alert';
import { ButtonDirective } from '@app/shared/components/button/button.directive';
import { FormField } from '@app/shared/components/form-field/form-field';
import { Pagination } from '@app/shared/components/pagination/pagination';
import { StatusBadge } from '@app/shared/components/status-badge/status-badge';
import { UiState } from '@app/shared/components/ui-state/ui-state';

interface SensitiveAction {
  readonly email: string;
  readonly execute: (confirmationEmail: string) => void;
}

@Component({
  selector: 'app-access-admin-page',
  imports: [
    Alert,
    ButtonDirective,
    DatePipe,
    Dialog,
    FormField,
    InputText,
    Pagination,
    ReactiveFormsModule,
    Select,
    StatusBadge,
    Table,
    UiState,
  ],
  templateUrl: './access-admin-page.html',
  styleUrl: './access-admin-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccessAdminPage {
  private readonly api = inject(AdminAccessApi);

  readonly page = signal<AdminAccessEntryPage | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly notice = signal('');
  readonly sensitiveAction = signal<SensitiveAction | null>(null);
  readonly confirmation = new FormControl('', { nonNullable: true });

  readonly filters = new FormGroup({
    search: new FormControl('', { nonNullable: true }),
    role: new FormControl<AccessRole | ''>('', { nonNullable: true }),
    status: new FormControl<AccessStatus | ''>('', { nonNullable: true }),
  });
  readonly createForm = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [
        (control) => Validators.required(control),
        (control) => Validators.email(control),
        (control) => Validators.maxLength(320)(control),
      ],
    }),
    role: new FormControl<AccessRole>('USER', { nonNullable: true }),
  });

  readonly roleOptions = [
    { label: 'All roles', value: '' },
    { label: 'User', value: 'USER' },
    { label: 'Admin', value: 'ADMIN' },
  ];
  readonly createRoleOptions = this.roleOptions.slice(1);
  readonly statusOptions = [
    { label: 'All statuses', value: '' },
    { label: 'Active', value: 'ACTIVE' },
    { label: 'Revoked', value: 'REVOKED' },
  ];

  constructor() {
    this.load(0);
  }

  load(page = 0): void {
    this.loading.set(true);
    this.error.set('');
    const filters = this.filters.getRawValue();
    this.api
      .list({
        search: filters.search.trim() || undefined,
        role: filters.role || undefined,
        status: filters.status || undefined,
        page,
        pageSize: 20,
      })
      .subscribe({
        next: (result) => {
          this.page.set(result);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Access entries could not be loaded.');
          this.loading.set(false);
        },
      });
  }

  clearFilters(): void {
    this.filters.reset({ search: '', role: '', status: '' });
    this.load(0);
  }

  requestCreate(): void {
    if (this.createForm.invalid) return;
    const request = this.createForm.getRawValue();
    if (request.role === 'ADMIN') {
      this.openSensitive(request.email, (confirmationEmail) =>
        this.create(request.email, request.role, confirmationEmail),
      );
      return;
    }
    this.create(request.email, request.role);
  }

  changeRole(entry: AdminAccessEntry): void {
    const role: AccessRole = entry.role === 'ADMIN' ? 'USER' : 'ADMIN';
    this.openSensitive(entry.email, (confirmationEmail) =>
      this.update(entry, role, entry.status, confirmationEmail),
    );
  }

  toggleStatus(entry: AdminAccessEntry): void {
    const status: AccessStatus = entry.status === 'ACTIVE' ? 'REVOKED' : 'ACTIVE';
    if (entry.role === 'ADMIN') {
      this.openSensitive(entry.email, (confirmationEmail) =>
        this.update(entry, entry.role, status, confirmationEmail),
      );
      return;
    }
    if (
      status === 'REVOKED' &&
      !window.confirm(`Revoke access for ${entry.email}? Their current session will remain active.`)
    ) {
      return;
    }
    this.update(entry, entry.role, status);
  }

  confirmSensitive(): void {
    const action = this.sensitiveAction();
    if (action?.email !== this.confirmation.value.trim().toLowerCase()) return;
    const confirmation = this.confirmation.value;
    this.closeSensitive();
    action.execute(confirmation);
  }

  closeSensitive(): void {
    this.sensitiveAction.set(null);
    this.confirmation.setValue('');
  }

  private openSensitive(email: string, execute: (confirmationEmail: string) => void): void {
    this.confirmation.setValue('');
    this.sensitiveAction.set({ email: email.trim().toLowerCase(), execute });
  }

  private create(email: string, role: AccessRole, confirmationEmail?: string): void {
    this.notice.set('');
    this.api.create({ email, role, confirmationEmail }).subscribe({
      next: () => {
        this.createForm.reset({ email: '', role: 'USER' });
        this.notice.set('Access entry saved.');
        this.load(0);
      },
      error: (error: HttpErrorResponse) => this.handleMutationError(error),
    });
  }

  private update(
    entry: AdminAccessEntry,
    role: AccessRole,
    status: AccessStatus,
    confirmationEmail?: string,
  ): void {
    this.notice.set('');
    this.api.update(entry, { role, status, confirmationEmail }).subscribe({
      next: () => {
        this.notice.set('Access entry updated.');
        this.load(this.page()?.page ?? 0);
      },
      error: (error: HttpErrorResponse) => this.handleMutationError(error),
    });
  }

  private handleMutationError(error: HttpErrorResponse): void {
    const code = (error.error as { code?: string } | null)?.code;
    if (code === 'RECENT_AUTHENTICATION_REQUIRED') {
      const returnTo = encodeURIComponent('/app/admin/access');
      window.location.assign(`/api/v1/auth/google?returnTo=${returnTo}&reauthenticate=true`);
      return;
    }
    const messages: Record<string, string> = {
      ACCESS_ENTRY_EXISTS: 'That email already has active access.',
      ACCESS_ENTRY_VERSION_CONFLICT: 'This entry changed. Reload and try again.',
      SELF_ADMIN_CHANGE_FORBIDDEN: 'Another administrator must change your access.',
      LAST_ADMIN_REQUIRED: 'At least one active administrator is required.',
      ADMIN_CONFIRMATION_REQUIRED: 'The confirmation email did not match.',
    };
    this.error.set(messages[code ?? ''] ?? 'The access change could not be completed.');
  }
}
