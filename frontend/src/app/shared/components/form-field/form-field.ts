import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  ViewEncapsulation,
} from '@angular/core';

@Component({
  selector: 'app-form-field',
  templateUrl: './form-field.html',
  styleUrl: './form-field.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
})
export class FormField {
  readonly inputId = input.required<string>();
  readonly label = input.required<string>();
  readonly hint = input<string>();
  readonly error = input<string>();
  readonly required = input(false);

  readonly hintId = computed(() => `${this.inputId()}-hint`);
  readonly errorId = computed(() => `${this.inputId()}-error`);
  readonly describedBy = computed(() =>
    [this.hint() ? this.hintId() : undefined, this.error() ? this.errorId() : undefined]
      .filter((value) => value !== undefined)
      .join(' '),
  );
}
