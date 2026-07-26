import { Dialog, DialogModule } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';

import { ActionMenu, ActionMenuItem } from '@app/shared/components/action-menu/action-menu';
import { Alert } from '@app/shared/components/alert/alert';
import {
  ButtonDirective,
  IconButtonDirective,
} from '@app/shared/components/button/button.directive';
import {
  ConfirmDialog,
  ConfirmDialogData,
} from '@app/shared/components/confirm-dialog/confirm-dialog';
import { FormField } from '@app/shared/components/form-field/form-field';
import { Pagination } from '@app/shared/components/pagination/pagination';
import { ResponsiveData } from '@app/shared/components/responsive-data/responsive-data';
import { Skeleton } from '@app/shared/components/skeleton/skeleton';
import { StatusBadge, StatusTone } from '@app/shared/components/status-badge/status-badge';
import { ToastService } from '@app/shared/components/toast/toast.service';
import { UiState } from '@app/shared/components/ui-state/ui-state';

import { AppShell } from './app-shell';

interface SourcePreview {
  readonly name: string;
  readonly type: string;
  readonly status: string;
  readonly tone: StatusTone;
  readonly contexts: number;
}

@Component({
  selector: 'app-foundation-page',
  imports: [
    ActionMenu,
    Alert,
    AppShell,
    ButtonDirective,
    DialogModule,
    FormField,
    IconButtonDirective,
    Pagination,
    ResponsiveData,
    Skeleton,
    StatusBadge,
    UiState,
  ],
  templateUrl: './foundation-page.html',
  styleUrl: './foundation-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FoundationPage {
  private readonly dialog = inject(Dialog);
  private readonly toasts = inject(ToastService);

  readonly currentPage = signal(1);
  readonly stats = [
    { label: 'Sources', value: '18', detail: '+3 this week' },
    { label: 'Saved cards', value: '384', detail: '42 from your corpus' },
    { label: 'Due today', value: '12', detail: 'About 6 minutes' },
    { label: 'Active decks', value: '4', detail: 'All synced' },
  ] as const;
  readonly sources: readonly SourcePreview[] = [
    {
      name: 'A Weekend in Madrid',
      type: 'YouTube captions',
      status: 'Ready',
      tone: 'success',
      contexts: 128,
    },
    {
      name: 'La casa de Bernarda Alba',
      type: 'PDF',
      status: 'Processing',
      tone: 'warning',
      contexts: 0,
    },
    {
      name: 'Notes from Sevilla',
      type: 'Pasted text',
      status: 'Ready',
      tone: 'success',
      contexts: 47,
    },
  ];
  readonly menuItems: readonly ActionMenuItem[] = [
    { id: 'rename', label: 'Rename source' },
    { id: 'archive', label: 'Archive source' },
    { id: 'delete', label: 'Delete source', danger: true },
  ];

  openDialog(): void {
    this.dialog.open<boolean, ConfirmDialogData>(ConfirmDialog, {
      data: {
        title: 'Add this example source?',
        description: 'This foundation dialog demonstrates focus trapping and restoration.',
        confirmLabel: 'Add source',
      },
      ariaLabel: 'Confirm source',
      autoFocus: 'button',
      restoreFocus: true,
    });
  }

  showToast(): void {
    this.toasts.show(
      'Export ready',
      'Spanish Core can be downloaded for the next six days.',
      'success',
    );
  }

  showSearchToast(query: string): void {
    this.toasts.show('Search submitted', `Searching the workspace for “${query}”.`);
  }

  showActionToast(action: string): void {
    this.toasts.show('Action selected', action.replaceAll('-', ' '));
  }
}
