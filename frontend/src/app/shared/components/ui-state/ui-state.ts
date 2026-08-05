import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ProgressSpinner } from 'primeng/progressspinner';

import { AppIcon } from '../icon/app-icon';

export type UiStateKind = 'loading' | 'empty' | 'error';

@Component({
  selector: 'app-ui-state',
  imports: [AppIcon, ProgressSpinner],
  templateUrl: './ui-state.html',
  styleUrl: './ui-state.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UiState {
  readonly kind = input.required<UiStateKind>();
  readonly title = input.required<string>();
  readonly description = input.required<string>();
}
