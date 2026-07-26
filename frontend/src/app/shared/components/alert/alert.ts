import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type AlertTone = 'info' | 'success' | 'warning' | 'danger';

@Component({
  selector: 'app-alert',
  templateUrl: './alert.html',
  styleUrl: './alert.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Alert {
  readonly title = input.required<string>();
  readonly tone = input<AlertTone>('info');
  readonly live = input<'off' | 'polite' | 'assertive'>('polite');
}
