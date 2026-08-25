import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Message } from 'primeng/message';

import { AppIcon, AppIconName } from '../icon/app-icon';

export type AlertTone = 'info' | 'success' | 'warning' | 'danger';

@Component({
  selector: 'app-alert',
  imports: [AppIcon, Message],
  templateUrl: './alert.html',
  styleUrl: './alert.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Alert {
  readonly title = input.required<string>();
  readonly tone = input<AlertTone>('info');
  readonly live = input<'off' | 'polite' | 'assertive'>('polite');
  readonly primeSeverity = computed(() => {
    const severityByTone = {
      info: 'info',
      success: 'success',
      warning: 'warn',
      danger: 'error',
    } satisfies Record<AlertTone, 'info' | 'success' | 'warn' | 'error'>;
    return severityByTone[this.tone()];
  });
  readonly icon = computed<AppIconName>(() => (this.tone() === 'success' ? 'check' : 'danger'));
}
