import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  forwardRef,
  input,
  output,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface SearchableSelectOption {
  readonly value: string;
  readonly label: string;
}

let nextSearchableSelectId = 0;

/**
 * Small ARIA combobox used for server-bounded option searches. Business data fetching stays in
 * the owning feature; this component owns only keyboard, focus, and form-control behavior.
 */
@Component({
  selector: 'app-searchable-select',
  templateUrl: './searchable-select.html',
  styleUrl: './searchable-select.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SearchableSelect),
      multi: true,
    },
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchableSelect implements ControlValueAccessor {
  readonly label = input.required<string>();
  readonly placeholder = input('');
  readonly options = input<readonly SearchableSelectOption[]>([]);
  readonly emptyLabel = input('No matching options');
  readonly searchChange = output<string>();

  readonly query = signal('');
  readonly open = signal(false);
  readonly activeIndex = signal(-1);
  readonly disabled = signal(false);
  readonly selectedValue = signal('');
  readonly listboxId = `searchable-select-${++nextSearchableSelectId}`;

  readonly activeOptionId = computed(() => {
    const option = this.options()[this.activeIndex()];
    return option ? `${this.listboxId}-option-${this.activeIndex()}` : null;
  });

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  constructor() {
    effect(() => {
      const selected = this.options().find((option) => option.value === this.selectedValue());
      if (selected && !this.open()) this.query.set(selected.label);
    });
  }

  writeValue(value: unknown): void {
    const nextValue = typeof value === 'string' ? value : '';
    this.selectedValue.set(nextValue);
    this.query.set(this.options().find((option) => option.value === nextValue)?.label ?? '');
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

  handleInput(value: string): void {
    this.query.set(value);
    this.open.set(true);
    this.activeIndex.set(this.options().length ? 0 : -1);
    this.searchChange.emit(value.trim());
  }

  handleInputEvent(event: Event): void {
    const target = event.target;
    if (target instanceof HTMLInputElement) this.handleInput(target.value);
  }

  handleFocus(): void {
    if (this.disabled()) return;
    this.open.set(true);
    this.activeIndex.set(this.selectedIndex());
  }

  handleKeydown(event: KeyboardEvent): void {
    if (this.disabled()) return;
    const options = this.options();
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      this.open.set(true);
      if (!options.length) {
        this.activeIndex.set(-1);
        return;
      }
      const direction = event.key === 'ArrowDown' ? 1 : -1;
      const start = this.activeIndex() < 0 ? (direction > 0 ? -1 : 0) : this.activeIndex();
      this.activeIndex.set((start + direction + options.length) % options.length);
      return;
    }
    if (event.key === 'Enter' && this.open() && this.activeIndex() >= 0) {
      event.preventDefault();
      const option = options[this.activeIndex()];
      if (option) this.select(option);
      return;
    }
    if (event.key === 'Escape') {
      event.preventDefault();
      this.restoreSelectedLabel();
      this.open.set(false);
    }
  }

  select(option: SearchableSelectOption): void {
    this.selectedValue.set(option.value);
    this.query.set(option.label);
    this.open.set(false);
    this.activeIndex.set(this.options().findIndex((candidate) => candidate.value === option.value));
    this.onChange(option.value);
    this.onTouched();
  }

  handleFocusOut(event: FocusEvent): void {
    const current = event.currentTarget;
    const relatedTarget = event.relatedTarget;
    if (
      current instanceof HTMLElement &&
      (relatedTarget === null || relatedTarget instanceof Node) &&
      current.contains(relatedTarget)
    ) {
      return;
    }
    this.restoreSelectedLabel();
    this.open.set(false);
    this.onTouched();
  }

  private selectedIndex(): number {
    const index = this.options().findIndex((option) => option.value === this.selectedValue());
    return index >= 0 ? index : this.options().length ? 0 : -1;
  }

  private restoreSelectedLabel(): void {
    const selected = this.options().find((option) => option.value === this.selectedValue());
    this.query.set(selected?.label ?? '');
  }
}
