import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-fatal-error-page',
  imports: [RouterLink],
  templateUrl: './fatal-error-page.html',
  styleUrl: './fatal-error-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FatalErrorPage {}
