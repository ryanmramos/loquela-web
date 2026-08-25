import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';

import { AppShell } from './app-shell';

describe('AppShell', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [MessageService, provideRouter([])],
    }).compileComponents();
  });

  it('provides landmarks, a skip link, and labelled navigation', () => {
    const fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();
    const shell = fixture.nativeElement as HTMLElement;

    expect(shell.querySelector<HTMLAnchorElement>('.skip-link')?.hash).toBe('#main-content');
    expect(shell.querySelector('main')?.id).toBe('main-content');
    expect(shell.querySelectorAll('nav[aria-label]').length).toBe(2);
    expect(shell.querySelector('.workspace-search')).toBeNull();
    expect(shell.querySelector('[role="search"]')).toBeNull();
    expect(shell.querySelector('.topbar__actions')).not.toBeNull();
  });
});
