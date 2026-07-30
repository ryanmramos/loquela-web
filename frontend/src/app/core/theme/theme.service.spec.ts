import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';

import { BrowserStorage } from '@core/storage/browser-storage';

import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  it('uses the system theme until the user selects an explicit preference', () => {
    const storage = { read: vi.fn(() => null), write: vi.fn() };
    const document = {
      documentElement: { dataset: {} as Record<string, string> },
      defaultView: {
        matchMedia: vi.fn(() => ({
          matches: true,
          addEventListener: vi.fn(),
        })),
      },
    };
    TestBed.configureTestingModule({
      providers: [
        ThemeService,
        { provide: BrowserStorage, useValue: storage },
        { provide: DOCUMENT, useValue: document },
      ],
    });
    const themes = TestBed.inject(ThemeService);

    themes.initialize();
    expect(themes.theme()).toBe('dark');

    themes.toggle();
    expect(themes.theme()).toBe('light');
    expect(storage.write).toHaveBeenCalledWith('color-theme', 'light');
    expect(document.documentElement.dataset['theme']).toBe('light');
  });
});
