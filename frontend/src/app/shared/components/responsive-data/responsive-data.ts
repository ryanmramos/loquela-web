import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-responsive-data',
  templateUrl: './responsive-data.html',
  styleUrl: './responsive-data.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResponsiveData {
  readonly label = input.required<string>();
}
