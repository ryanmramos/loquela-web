import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { SearchableSelect, SearchableSelectOption } from './searchable-select';

@Component({
  imports: [ReactiveFormsModule, SearchableSelect],
  template: `
    <app-searchable-select
      label="Deck"
      [formControl]="control"
      [options]="options"
      (searchChange)="lastSearch = $event"
    />
  `,
})
class Host {
  readonly control = new FormControl('deck-1', { nonNullable: true });
  readonly options: SearchableSelectOption[] = [
    { value: 'deck-1', label: 'Core' },
    { value: 'deck-2', label: 'Travel' },
  ];
  lastSearch = '';
}

describe('SearchableSelect', () => {
  let fixture: ComponentFixture<Host>;
  let input: HTMLInputElement;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [Host] });
    fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    input = element.querySelector('input')!;
  });

  it('exposes the selected label and ARIA combobox contract', () => {
    expect(input.value).toBe('Core');
    expect(input.getAttribute('role')).toBe('combobox');
    expect(input.getAttribute('aria-controls')).toBeTruthy();
  });

  it('searches, supports arrow selection, and updates the form value', () => {
    input.value = 'Co';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    fixture.detectChanges();

    expect(fixture.componentInstance.lastSearch).toBe('Co');
    expect(fixture.componentInstance.control.value).toBe('deck-2');
  });

  it('keeps the active option unset when an empty result set receives arrow keys', () => {
    fixture.componentInstance.options.splice(0);
    fixture.detectChanges();
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    fixture.detectChanges();

    expect(input.getAttribute('aria-activedescendant')).toBeNull();
  });
});
