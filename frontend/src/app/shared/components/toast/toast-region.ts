import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { IconButtonDirective } from '../button/button.directive';
import { ToastService } from './toast.service';

@Component({
  selector: 'app-toast-region',
  imports: [IconButtonDirective],
  templateUrl: './toast-region.html',
  styleUrl: './toast-region.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToastRegion {
  readonly toasts = inject(ToastService);
}
