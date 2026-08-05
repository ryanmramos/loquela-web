import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  ViewEncapsulation,
} from '@angular/core';
import { MenuItem } from 'primeng/api';
import { Menu } from 'primeng/menu';

import { IconButtonDirective } from '../button/button.directive';
import { AppIcon } from '../icon/app-icon';
import { TranslationService } from '@core/i18n/translation.service';

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
  imports: [AppIcon, IconButtonDirective, Menu],
  templateUrl: './action-menu.html',
  styleUrl: './action-menu.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
})
export class ActionMenu {
  private readonly translations = inject(TranslationService);
  readonly label = input('Open actions');
  readonly items = input.required<readonly ActionMenuItem[]>();
  readonly itemSelected = output<string>();
  readonly model = computed<MenuItem[]>(() =>
    this.items().map((item) => ({
      label: item.labelKey ? this.translations.translate(item.labelKey) : item.label,
      styleClass: item.danger ? 'menu__item--danger' : undefined,
      command: () => this.itemSelected.emit(item.id),
    })),
  );
}
