import { Directive, input } from '@angular/core';
import { ButtonDirective as PrimeButtonDirective } from 'primeng/button';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';

@Directive({
  selector: 'button[appButton], a[appButton]',
  hostDirectives: [PrimeButtonDirective],
  host: {
    class: 'loquela-button',
    '[class.loquela-button--secondary]': "variant() === 'secondary'",
    '[class.loquela-button--ghost]': "variant() === 'ghost'",
    '[class.loquela-button--danger]': "variant() === 'danger'",
  },
})
export class ButtonDirective {
  readonly variant = input<ButtonVariant>('primary');
}

@Directive({
  selector: 'button[appIconButton], a[appIconButton]',
  hostDirectives: [PrimeButtonDirective],
  host: {
    class: 'loquela-icon-button',
  },
})
export class IconButtonDirective {}
