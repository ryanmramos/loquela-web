import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Skeleton as PrimeSkeleton } from 'primeng/skeleton';

@Component({
  selector: 'app-skeleton',
  imports: [PrimeSkeleton],
  template: '<p-skeleton class="skeleton" [width]="width()" height="1rem" aria-hidden="true" />',
  styleUrl: './skeleton.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Skeleton {
  readonly width = input('100%');
}
