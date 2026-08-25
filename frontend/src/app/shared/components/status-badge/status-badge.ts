import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Tag } from 'primeng/tag';

export type StatusTone = 'neutral' | 'success' | 'info' | 'warning' | 'danger';

@Component({
  selector: 'app-status-badge',
  imports: [Tag],
  templateUrl: './status-badge.html',
  styleUrl: './status-badge.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusBadge {
  readonly tone = input<StatusTone>('neutral');
  readonly severity = computed(() => {
    const severityByTone = {
      neutral: 'secondary',
      success: 'success',
      info: 'info',
      warning: 'warn',
      danger: 'danger',
    } satisfies Record<StatusTone, 'secondary' | 'success' | 'info' | 'warn' | 'danger'>;
    return severityByTone[this.tone()];
  });
}
