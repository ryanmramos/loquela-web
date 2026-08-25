import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Table } from 'primeng/table';
import { finalize } from 'rxjs';

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
import { ResponsiveData } from '@app/shared/components/responsive-data/responsive-data';
import { SearchInput } from '@app/shared/components/search-input/search-input';
import { StatusBadge } from '@app/shared/components/status-badge/status-badge';
import { UiState } from '@app/shared/components/ui-state/ui-state';
import { TranslatePipe } from '@core/i18n/translate.pipe';
import { TranslationService } from '@core/i18n/translation.service';

type SensitiveActionKind = 'role' | 'revoke';

interface SensitiveAction {
  readonly email: string;
  readonly kind: SensitiveActionKind;
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
    ResponsiveData,
    Select,
    SearchInput,
    StatusBadge,
    Table,
    TranslatePipe,
    UiState,
  ],
  templateUrl: './access-admin-page.html',
  styleUrl: './access-admin-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccessAdminPage {
  private readonly api = inject(AdminAccessApi);
  private readonly translations = inject(TranslationService);

  readonly page = signal<AdminAccessEntryPage | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly notice = signal('');
  readonly sensitiveAction = signal<SensitiveAction | null>(null);
  readonly mutating = signal(false);
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

  readonly roleOptions = computed(() => [
    { label: this.translations.translate('adminAccess.allRoles'), value: '' },
    { label: this.translations.translate('adminAccess.userRole'), value: 'USER' },
    { label: this.translations.translate('adminAccess.adminRole'), value: 'ADMIN' },
  ]);
  readonly createRoleOptions = computed(() => this.roleOptions().slice(1));
  readonly statusOptions = computed(() => [
    { label: this.translations.translate('adminAccess.allStatuses'), value: '' },
    { label: this.translations.translate('adminAccess.activeStatus'), value: 'ACTIVE' },
    { label: this.translations.translate('adminAccess.revokedStatus'), value: 'REVOKED' },
  ]);

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
          this.error.set(this.translations.translate('adminAccess.loadError'));
          this.loading.set(false);
        },
      });
  }

  clearFilters(): void {
    this.filters.reset({ search: '', role: '', status: '' });
    this.load(0);
  }

  hasActiveFilters(): boolean {
    const filters = this.filters.getRawValue();
    return Boolean(filters.search.trim() || filters.role || filters.status);
  }

  requestCreate(): void {
    if (this.createForm.invalid) return;
    const request = this.createForm.getRawValue();
    if (request.role === 'ADMIN') {
      this.openSensitive(request.email, 'role', (confirmationEmail) =>
        this.create(request.email, request.role, confirmationEmail),
      );
      return;
    }
    this.create(request.email, request.role);
  }

  changeRole(entry: AdminAccessEntry): void {
    const role: AccessRole = entry.role === 'ADMIN' ? 'USER' : 'ADMIN';
    this.openSensitive(entry.email, 'role', (confirmationEmail) =>
      this.update(entry, role, entry.status, confirmationEmail),
    );
  }

  toggleStatus(entry: AdminAccessEntry): void {
    const status: AccessStatus = entry.status === 'ACTIVE' ? 'REVOKED' : 'ACTIVE';
    if (status === 'REVOKED') {
      this.openSensitive(entry.email, 'revoke', (confirmationEmail) =>
        this.update(entry, entry.role, status, confirmationEmail),
      );
      return;
    }
    this.update(entry, entry.role, status);
  }

  confirmSensitive(): void {
    const action = this.sensitiveAction();
    if (!action || !this.canConfirmSensitive()) return;
    const confirmation = this.confirmation.value;
    this.closeSensitive();
    action.execute(confirmation);
  }

  canConfirmSensitive(): boolean {
    const action = this.sensitiveAction();
    return action !== null && action.email === this.confirmation.value.trim().toLowerCase();
  }

  closeSensitive(): void {
    this.sensitiveAction.set(null);
    this.confirmation.setValue('');
  }

  private openSensitive(
    email: string,
    kind: SensitiveActionKind,
    execute: (confirmationEmail: string) => void,
  ): void {
    this.confirmation.setValue('');
    this.sensitiveAction.set({ email: email.trim().toLowerCase(), kind, execute });
  }

  private create(email: string, role: AccessRole, confirmationEmail?: string): void {
    this.notice.set('');
    this.mutating.set(true);
    this.api
      .create({ email, role, confirmationEmail })
      .pipe(finalize(() => this.mutating.set(false)))
      .subscribe({
        next: () => {
          this.createForm.reset({ email: '', role: 'USER' });
          this.notice.set(this.translations.translate('adminAccess.savedNotice'));
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
    this.mutating.set(true);
    this.api
      .update(entry, { role, status, confirmationEmail })
      .pipe(finalize(() => this.mutating.set(false)))
      .subscribe({
        next: () => {
          this.notice.set(this.translations.translate('adminAccess.updatedNotice'));
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
      ACCESS_ENTRY_EXISTS: this.translations.translate('adminAccess.existsError'),
      ACCESS_ENTRY_VERSION_CONFLICT: this.translations.translate(
        'adminAccess.versionConflictError',
      ),
      SELF_ADMIN_CHANGE_FORBIDDEN: this.translations.translate('adminAccess.selfAdminError'),
      LAST_ADMIN_REQUIRED: this.translations.translate('adminAccess.lastAdminError'),
      ADMIN_CONFIRMATION_REQUIRED: this.translations.translate('adminAccess.confirmationError'),
    };
    this.error.set(messages[code ?? ''] ?? this.translations.translate('adminAccess.changeError'));
  }
}
