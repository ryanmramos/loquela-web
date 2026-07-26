import { DialogRef, DIALOG_DATA } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { ButtonDirective } from '../button/button.directive';

export interface ConfirmDialogData {
  readonly title: string;
  readonly description: string;
  readonly confirmLabel: string;
}

@Component({
  selector: 'app-confirm-dialog',
  imports: [ButtonDirective],
  templateUrl: './confirm-dialog.html',
  styleUrl: './confirm-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDialog {
  readonly data = inject<ConfirmDialogData>(DIALOG_DATA);
  private readonly dialogRef = inject<DialogRef<boolean>>(DialogRef);

  close(confirmed: boolean): void {
    this.dialogRef.close(confirmed);
  }
}
