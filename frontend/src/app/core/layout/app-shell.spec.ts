import { TestBed } from '@angular/core/testing';

import { AppShell } from './app-shell';

describe('AppShell', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [AppShell] }).compileComponents();
  });

  it('provides landmarks, a skip link, and labelled navigation', () => {
    const fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();
    const shell = fixture.nativeElement as HTMLElement;

    expect(shell.querySelector<HTMLAnchorElement>('.skip-link')?.hash).toBe('#main-content');
    expect(shell.querySelector('main')?.id).toBe('main-content');
    expect(shell.querySelectorAll('nav[aria-label]').length).toBe(2);
    expect(shell.querySelector('input')?.getAttribute('aria-label')).toBeNull();
    expect(shell.querySelector('label[for="workspace-search"]')?.textContent).toContain(
      'Search your workspace',
    );
  });

  it('emits a trimmed search query', () => {
    const fixture = TestBed.createComponent(AppShell);
    const emitted = vi.fn();
    fixture.componentInstance.searchSubmitted.subscribe(emitted);
    fixture.detectChanges();

    const form = (fixture.nativeElement as HTMLElement).querySelector('form');
    const input = (fixture.nativeElement as HTMLElement).querySelector('input');
    if (!form || !input) {
      throw new Error('Expected the shell search form.');
    }
    input.value = '  sobremesa  ';
    form.dispatchEvent(new SubmitEvent('submit'));

    expect(emitted).toHaveBeenCalledWith('sobremesa');
  });
});
