import {
  ChangeDetectionStrategy,
  Component,
  computed,
  forwardRef,
  input,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { InputText } from 'primeng/inputtext';

import { AppIcon } from '@app/shared/components/icon/app-icon';

@Component({
  selector: 'app-search-input',
  imports: [AppIcon, InputText],
  templateUrl: './search-input.html',
  styleUrl: './search-input.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SearchInput),
      multi: true,
    },
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchInput implements ControlValueAccessor {
  readonly inputId = input.required<string>();
  readonly label = input.required<string>();
  readonly placeholder = input('');
  readonly hint = input('');
  readonly hideLabel = input(false);
  readonly compact = input(false);
  readonly autocomplete = input('off');
  readonly spellcheck = input(false);
  readonly maxLength = input<number | null>(null);

  readonly value = signal('');
  readonly disabled = signal(false);
  readonly hintId = computed(() => `${this.inputId()}-hint`);

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: unknown): void {
    this.value.set(typeof value === 'string' ? value : '');
  }

  registerOnChange(callback: (value: string) => void): void {
    this.onChange = callback;
  }

  registerOnTouched(callback: () => void): void {
    this.onTouched = callback;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }

  handleInput(event: Event): void {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) return;
    this.value.set(target.value);
    this.onChange(target.value);
  }

  handleBlur(): void {
    this.onTouched();
  }
}
