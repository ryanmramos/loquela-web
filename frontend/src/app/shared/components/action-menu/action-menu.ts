import { CdkMenuModule } from '@angular/cdk/menu';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { IconButtonDirective } from '../button/button.directive';
import { TranslatePipe } from '@core/i18n/translate.pipe';

export interface ActionMenuItem {
  readonly id: string;
  readonly label: string;
  readonly labelKey?: Parameters<
    import('@core/i18n/translation.service').TranslationService['translate']
  >[0];
  readonly danger?: boolean;
}

@Component({
  selector: 'app-action-menu',
  imports: [CdkMenuModule, IconButtonDirective, TranslatePipe],
  templateUrl: './action-menu.html',
  styleUrl: './action-menu.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActionMenu {
  readonly label = input('Open actions');
  readonly items = input.required<readonly ActionMenuItem[]>();
  readonly itemSelected = output<string>();
}
