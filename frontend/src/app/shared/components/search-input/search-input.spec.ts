import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { SearchInput } from './search-input';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, SearchInput],
  template: `
    <app-search-input
      inputId="term-search"
      label="Search"
      placeholder="Enter a word"
      [formControl]="control"
    />
  `,
})
class SearchInputHost {
  readonly control = new FormControl('sobremesa', { nonNullable: true });
}

describe('SearchInput', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, SearchInput],
    }).compileComponents();
  });

  it('renders an accessible labelled search control with an optional hint', () => {
    const fixture = TestBed.createComponent(SearchInput);
    fixture.componentRef.setInput('inputId', 'workspace-search');
    fixture.componentRef.setInput('label', 'Search your workspace');
    fixture.componentRef.setInput('hint', 'Search by name');
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const input = element.querySelector('input');

    expect(element.querySelector('label')?.getAttribute('for')).toBe('workspace-search');
    expect(input?.getAttribute('aria-describedby')).toBe('workspace-search-hint');
    expect(element.querySelector('#workspace-search-hint')?.textContent).toContain(
      'Search by name',
    );
  });

  it('propagates value and disabled state through a reactive form control', () => {
    const fixture = TestBed.createComponent(SearchInputHost);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const input = element.querySelector<HTMLInputElement>('input')!;
    expect(input.value).toBe('sobremesa');

    input.value = 'contexto';
    input.dispatchEvent(new Event('input'));
    expect(fixture.componentInstance.control.value).toBe('contexto');

    fixture.componentInstance.control.disable();
    fixture.detectChanges();
    expect(input.disabled).toBe(true);
  });
});
