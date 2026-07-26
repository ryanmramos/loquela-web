import { TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';

import { AppTitleStrategy } from './app-title-strategy';

describe('AppTitleStrategy', () => {
  it('combines the route title with the product name', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'settings', title: 'Settings', component: TestRoute }]),
        AppTitleStrategy,
      ],
    });
    const strategy = TestBed.inject(AppTitleStrategy);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/settings');
    strategy.updateTitle(router.routerState.snapshot);

    expect(TestBed.inject(Title).getTitle()).toBe('Settings · Loquela');
  });
});

@Component({ template: '' })
class TestRoute {}
