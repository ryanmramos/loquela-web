import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type UiStateKind = 'loading' | 'empty' | 'error';

@Component({
  selector: 'app-ui-state',
  templateUrl: './ui-state.html',
  styleUrl: './ui-state.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UiState {
  readonly kind = input.required<UiStateKind>();
  readonly title = input.required<string>();
  readonly description = input.required<string>();
}
