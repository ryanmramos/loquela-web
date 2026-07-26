import { Directive, input } from '@angular/core';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';

@Directive({
  selector: 'button[appButton], a[appButton]',
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
  host: {
    class: 'loquela-icon-button',
  },
})
export class IconButtonDirective {}
