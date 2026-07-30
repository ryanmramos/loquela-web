import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { LanguageSettings, SettingsApi } from '@api/api-adapters/settings-api';
import { SessionFacade } from '@core/auth/session-facade';

import { SettingsPage } from './settings-page';

const settings: LanguageSettings = {
  languagePair: { sourceLanguage: 'es', definitionLanguage: 'en' },
  supportedPairs: [
    { sourceLanguage: 'es', definitionLanguage: 'en' },
    { sourceLanguage: 'es', definitionLanguage: 'es' },
    { sourceLanguage: 'en', definitionLanguage: 'es' },
  ],
};

describe('SettingsPage', () => {
  const getLanguages = vi.fn(() => of(settings));
  const updateLanguages = vi.fn(() => of(settings));

  beforeEach(() => {
    getLanguages.mockClear();
    updateLanguages.mockClear();
    TestBed.configureTestingModule({
      imports: [SettingsPage],
      providers: [
        {
          provide: SettingsApi,
          useValue: { getLanguages, updateLanguages },
        },
        {
          provide: SessionFacade,
          useValue: { updateInterfaceLanguage: () => of(undefined) },
        },
      ],
    });
  });

  it('loads supported pairs and starts with a pristine language form', () => {
    const component = TestBed.createComponent(SettingsPage).componentInstance;

    expect(component.languageForm.getRawValue()).toEqual(settings.languagePair);
    expect(component.languageForm.pristine).toBe(true);
    expect(component.definitionLanguages('en')).toEqual(['es']);
  });

  it('keeps the selected source and definition combination supported', () => {
    const component = TestBed.createComponent(SettingsPage).componentInstance;

    component.languageForm.controls.sourceLanguage.setValue('en');
    component.languageForm.markAsDirty();
    component.sourceLanguageChanged();

    expect(component.languageForm.getRawValue()).toEqual({
      sourceLanguage: 'en',
      definitionLanguage: 'es',
    });
  });

  it('tracks save success independently and resets the form to pristine', () => {
    const component = TestBed.createComponent(SettingsPage).componentInstance;
    component.languageForm.controls.definitionLanguage.setValue('es');
    component.languageForm.markAsDirty();

    component.saveLanguageSettings();

    expect(updateLanguages).toHaveBeenCalledWith({
      sourceLanguage: 'es',
      definitionLanguage: 'es',
    });
    expect(component.languageSaved()).toBe(true);
    expect(component.languageForm.pristine).toBe(true);
  });

  it('preserves dirty edits when saving fails', () => {
    updateLanguages.mockReturnValueOnce(throwError(() => new Error('offline')));
    const component = TestBed.createComponent(SettingsPage).componentInstance;
    component.languageForm.controls.definitionLanguage.setValue('es');
    component.languageForm.markAsDirty();

    component.saveLanguageSettings();

    expect(component.languageError()).toBe(true);
    expect(component.languageForm.dirty).toBe(true);
    expect(component.languageForm.getRawValue().definitionLanguage).toBe('es');
  });
});
